/**
 * Copyright 2022  Nikita S. <nikita@saraeff.net>
 * <p>
 * This file is part of Telegraher.
 * <p>
 * Telegraher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Telegraher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Telegraher.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.evildayz.code.telegraher;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.evildayz.code.telegraher.devicespoofing.DSMainActivity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.QrActivity;

import java.util.ArrayList;

public class TelegraherSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private RecyclerListView listView;

    private TelegraherSettingsActivity.ListAdapter adapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int showLabelTelegraherMenuRow;
    private int showTelegraherMenuRow;

    private int voiceLabelRow;
    private int voiceHDRow;
    private int voiceBadmanRow;

    private int voipLabelRow;
    private int voipHDRow;

    private int profileLabelRow;
    private int profileUIDRow;
    private int profileDCIDRow;
    private int profileSBRow;

    private int chatLabelRow;
    private int chatDeleteMarkRow;
    private int chatSBFullRow;

    private int videoLabelRoundBitrateRow;
    private int videoRoundBitrateMultRow;
    private int videoLabelRoundSizeRow;
    private int videoRoundSizeMultRow;

    private int accountLabelRow;
    private int accountExtendVanillaRow;

    private int deviceSpoofingLabelRow;
    private int deviceSpoofingBrand;
    private int deviceSpoofingModel;
    private int deviceSpoofingOS;
    private int deviceSpoofingResetGlobalLabelRow;

    private int killMeLabelRow;

    private int rowCount = 0;

    @Override
    public boolean onFragmentCreate() {
        showLabelTelegraherMenuRow = rowCount++;
        showTelegraherMenuRow = rowCount++;
        voiceLabelRow = rowCount++;
        voiceHDRow = rowCount++;
        voiceBadmanRow = rowCount++;

        voipLabelRow = -1;
        voipHDRow = -1;

        profileLabelRow = rowCount++;
        profileUIDRow = rowCount++;
        profileDCIDRow = rowCount++;
        profileSBRow = rowCount++;

        chatLabelRow = rowCount++;
        chatDeleteMarkRow = rowCount++;
        chatSBFullRow = rowCount++;

        accountLabelRow = rowCount++;
        accountExtendVanillaRow = rowCount++;

        videoLabelRoundBitrateRow = rowCount++;
        videoRoundBitrateMultRow = rowCount++;
        videoLabelRoundSizeRow = rowCount++;
        videoRoundSizeMultRow = rowCount++;

        deviceSpoofingLabelRow = rowCount++;
        deviceSpoofingBrand = rowCount++;
        deviceSpoofingModel = rowCount++;
        deviceSpoofingOS = rowCount++;
        deviceSpoofingResetGlobalLabelRow = rowCount++;

        killMeLabelRow = rowCount++;

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.telegraherSettingsUpdated);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.telegraherSettingsUpdated);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Telegraher settings \uD83D\uDCA6");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new TelegraherSettingsActivity.ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return;
            }
            if (false) {
                //durov relogin!
            } else if (position == voiceHDRow) {
                SharedPreferences preferences = MessagesController.getGlobalTelegraherSettings();
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableVoiceHD", false);
                editor.putBoolean("EnableVoiceHD", !enabled);
                if (enabled) editor.putBoolean("EnableVoiceBadman", false);
                editor.commit();
            } else if (position == voiceBadmanRow) {
                SharedPreferences preferences = MessagesController.getGlobalTelegraherSettings();
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableVoiceBadman", false);
                editor.putBoolean("EnableVoiceBadman", preferences.getBoolean("EnableVoiceHD", false) && !enabled);
                if (!preferences.getBoolean("EnableVoiceHD", false)) enabled = true;
                editor.commit();
            } else if (position == voipHDRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableVoIPHD", false);
                editor.putBoolean("EnableVoIPHD", !enabled);
                editor.commit();
            } else if (position == profileUIDRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableProfileUID", true);
                editor.putBoolean("EnableProfileUID", !enabled);
                editor.commit();
            } else if (position == profileDCIDRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableProfileDCID", true);
                editor.putBoolean("EnableProfileDCID", !enabled);
                editor.commit();
            } else if (position == profileSBRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableProfileSB", true);
                editor.putBoolean("EnableProfileSB", !enabled);
                editor.commit();
            } else if (position == chatDeleteMarkRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableChatDeleteMark", true);
                editor.putBoolean("EnableChatDeleteMark", !enabled);
                editor.commit();
            } else if (position == chatSBFullRow) {
                SharedPreferences preferences = MessagesController.getTelegraherSettings(currentAccount);
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableChatSBFull", false);
                editor.putBoolean("EnableChatSBFull", !enabled);
                editor.commit();
            } else if (position == accountExtendVanillaRow) {
                SharedPreferences preferences = MessagesController.getGlobalTelegraherSettings();
                SharedPreferences.Editor editor = preferences.edit();
                enabled = preferences.getBoolean("EnableAccountExtendVanilla", false);
                editor.putBoolean("EnableAccountExtendVanilla", !enabled);
                editor.commit();
            } else if (position == killMeLabelRow) {
                killThatApp();
            } else if (position == deviceSpoofingBrand) {
                showDSAlert(0);
            } else if (position == deviceSpoofingModel) {
                showDSAlert(1);
            } else if (position == deviceSpoofingOS) {
                showDSAlert(2);
            } else if (position == deviceSpoofingResetGlobalLabelRow) {
                showDSResetGlobalAlert();
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!enabled);
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (false) {
            //durov relogin!
        } else if (id == NotificationCenter.telegraherSettingsUpdated) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return !(
                    position == voiceLabelRow || position == voipLabelRow);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new TextDetailCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    } else if (position == voiceLabelRow) {
                        headerCell.setText("Voice message section");
                    } else if (position == voipLabelRow) {
                        headerCell.setText("VoIP calls section");
                    } else if (position == profileLabelRow) {
                        headerCell.setText("Profile section");
                    } else if (position == chatLabelRow) {
                        headerCell.setText("Chat section");
                    } else if (position == videoLabelRoundBitrateRow) {
                        headerCell.setText("* Round video bitrate");
                    } else if (position == videoLabelRoundSizeRow) {
                        headerCell.setText("* Round video size");
                    } else if (position == accountLabelRow) {
                        headerCell.setText("Account section");
                    } else if (position == deviceSpoofingLabelRow) {
                        headerCell.setText("Device spoofing section");
                    } else if (position == deviceSpoofingResetGlobalLabelRow) {
                        headerCell.setText("Reset global spoofing values");
                    } else if (position == showLabelTelegraherMenuRow) {
                        headerCell.setText("* Show Telegraher menu");
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences localPreps = MessagesController.getTelegraherSettings(currentAccount);
                    SharedPreferences globalPreps = MessagesController.getGlobalTelegraherSettings();
                    if (false) {
                        //durov relogin!
                    } else if (position == voiceHDRow) {
                        checkCell.setTextAndCheck("* HD Voice", globalPreps.getBoolean("EnableVoiceHD", false), false);
                    } else if (position == voiceBadmanRow) {
                        checkCell.setTextAndCheck("* \uD83E\uDD87 Voice (slow)", globalPreps.getBoolean("EnableVoiceBadman", false), true);
                    } else if (position == voipHDRow) {
                        checkCell.setTextAndCheck("HD calls", localPreps.getBoolean("EnableVoIPHD", false), true);
                    } else if (position == profileUIDRow) {
                        checkCell.setTextAndCheck("Show numeric ID", localPreps.getBoolean("EnableProfileUID", true), true);
                    } else if (position == profileDCIDRow) {
                        checkCell.setTextAndCheck("Show DC ID", localPreps.getBoolean("EnableProfileDCID", true), true);
                    } else if (position == profileSBRow) {
                        checkCell.setTextAndCheck("Show Shadowban", localPreps.getBoolean("EnableProfileSB", true), true);
                    } else if (position == chatDeleteMarkRow) {
                        checkCell.setTextAndCheck(String.format("Show `%s` mark", LocaleController.getString("DeletedMessage", R.string.DeletedMessage)), localPreps.getBoolean("EnableChatDeleteMark", true), true);
                    } else if (position == accountExtendVanillaRow) {
                        checkCell.setTextAndCheck("* 3+", globalPreps.getBoolean("EnableAccountExtendVanilla", false), true);
                    } else if (position == chatSBFullRow) {
                        checkCell.setTextAndCheck("Full ShadowBan \uD83D\uDE48", localPreps.getBoolean("EnableChatSBFull", false), true);
                    }
                    break;
                }
                case 5: {
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    } else if (position == killMeLabelRow) {
                        textSettingsCell.setCanDisable(false);
                        textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
                        textSettingsCell.setText("Kill the APP", false);
                    }
                    break;
                }
                case 6: {
                    SlideChooseView slideChooseView = (SlideChooseView) holder.itemView;
                    if (false) {
                        //durov relogin!
                    } else if (position == videoRoundBitrateMultRow) {
                        String[] strings = new String[]{"x0.5", "x1", "x2", "x4"};
                        slideChooseView.setOptions(MessagesController.getTelegraherSettings(currentAccount).getInt("VideoRoundBitrateMult", 1), strings);
                        slideChooseView.setCallback(new SlideChooseView.Callback() {
                            @Override
                            public void onOptionSelected(int index) {
                                SharedPreferences localTh = MessagesController.getTelegraherSettings(currentAccount);
                                SharedPreferences localMain = MessagesController.getMainSettings(currentAccount);
                                SharedPreferences.Editor editor = localTh.edit();
                                editor.putInt("VideoRoundBitrateMult", index);
                                editor.commit();
                                editor = localMain.edit();
                                editor.putInt("roundVideoBitrate", (int) (1000 * ThePenisMightierThanTheSword.getVideoRoundMult(index)));
                                editor.putInt("roundAudioBitrate", (int) (64 * ThePenisMightierThanTheSword.getVideoRoundMult(index)));
                                editor.commit();
                                MessagesController.getInstance(currentAccount).roundVideoBitrate = MessagesController.getMainSettings(currentAccount).getInt("roundVideoBitrate", 1000);
                                MessagesController.getInstance(currentAccount).roundAudioBitrate = MessagesController.getMainSettings(currentAccount).getInt("roundAudioBitrate", 64);
                                System.out.printf("HEY %d %d %d%n", MessagesController.getMainSettings(currentAccount).getInt("roundVideoSize", 384)
                                        , MessagesController.getMainSettings(currentAccount).getInt("roundVideoBitrate", 1000)
                                        , MessagesController.getMainSettings(currentAccount).getInt("roundAudioBitrate", 64));
                            }
                        });
                    } else if (position == videoRoundSizeMultRow) {
                        String[] strings = new String[]{"x0.5", "x1", "x2"};
                        slideChooseView.setOptions(MessagesController.getTelegraherSettings(currentAccount).getInt("VideoRoundSizeMult", 1), strings);
                        slideChooseView.setCallback(new SlideChooseView.Callback() {
                            @Override
                            public void onOptionSelected(int index) {
                                SharedPreferences localTh = MessagesController.getTelegraherSettings(currentAccount);
                                SharedPreferences localMain = MessagesController.getMainSettings(currentAccount);
                                SharedPreferences.Editor editor = localTh.edit();
                                editor.putInt("VideoRoundSizeMult", index);
                                editor.commit();
                                editor = localMain.edit();
                                editor.putInt("roundVideoSize", ThePenisMightierThanTheSword.getVideoRoundSize(index));
                                editor.commit();
                                MessagesController.getInstance(currentAccount).roundVideoSize = MessagesController.getMainSettings(currentAccount).getInt("roundVideoSize", 384);
                                System.out.printf("HEY %d %d %d%n", MessagesController.getMainSettings(currentAccount).getInt("roundVideoSize", 384)
                                        , MessagesController.getMainSettings(currentAccount).getInt("roundVideoBitrate", 1000)
                                        , MessagesController.getMainSettings(currentAccount).getInt("roundAudioBitrate", 64));
                            }
                        });
                    } else if (position == showTelegraherMenuRow) {
                        String[] strings = new String[]{"Both", "Settings", "Storage Usage", "The Void"};
                        slideChooseView.setOptions(MessagesController.getGlobalTelegraherSettings().getInt("ShowTelegraherMenu2", 0), strings);
                        slideChooseView.setCallback(new SlideChooseView.Callback() {
                            @Override
                            public void onOptionSelected(int index) {
                                SharedPreferences globalTh = MessagesController.getGlobalTelegraherSettings();
                                SharedPreferences.Editor editor = globalTh.edit();
                                editor.putInt("ShowTelegraherMenu2", index);
                                editor.commit();
                            }
                        });
                    }
                    break;
                }
                case 7: {
                    TextDetailCell textDetailCell = (TextDetailCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    } else if (position == deviceSpoofingBrand) {
                        textDetailCell.setContentDescriptionValueFirst(true);
                        textDetailCell.setTextAndValue(UserConfig.hmGetBrand(-1), String.format("Device Brand for current account is `%s`", UserConfig.hmGetBrand(currentAccount)), false);
                    } else if (position == deviceSpoofingModel) {
                        textDetailCell.setContentDescriptionValueFirst(true);
                        textDetailCell.setImageClickListener(TelegraherSettingsActivity.this::onTextDetailCellImageClicked);
                        textDetailCell.setTextAndValue(UserConfig.hmGetModel(-1), String.format("Device Model for current account is `%s`", UserConfig.hmGetModel(currentAccount)), false);
                    } else if (position == deviceSpoofingOS) {
                        textDetailCell.setContentDescriptionValueFirst(true);
                        textDetailCell.setImageClickListener(TelegraherSettingsActivity.this::onTextDetailCellImageClicked);
                        textDetailCell.setTextAndValue(UserConfig.hmGetOS(-1), String.format("Device OS for current account is `%s`", UserConfig.hmGetOS(currentAccount)), false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (
                    position == showLabelTelegraherMenuRow
                            || position == voiceLabelRow || position == voipLabelRow
                            || position == profileLabelRow
                            || position == chatLabelRow
                            || position == accountLabelRow
                            || position == deviceSpoofingLabelRow
                            || position == deviceSpoofingResetGlobalLabelRow
                            || position == videoLabelRoundBitrateRow
                            || position == videoLabelRoundSizeRow
            ) {
                return 0;
            } else if (
                    position == voiceHDRow || position == voiceBadmanRow || position == voipHDRow
                            || position == profileUIDRow || position == profileDCIDRow || position == profileSBRow
                            || position == chatDeleteMarkRow || position == accountExtendVanillaRow || position == chatSBFullRow
            ) {
                return 1;
            } else if (position == killMeLabelRow) {
                return 5;
            } else if (
                    position == showTelegraherMenuRow
                            || position == videoRoundBitrateMultRow || position == videoRoundSizeMultRow) {
                return 6;
            } else if (position == deviceSpoofingBrand || position == deviceSpoofingModel || position == deviceSpoofingOS) {
                return 7;
            } else
                return 1337;
        }
    }

    private void onTextDetailCellImageClicked(View view) {
        View parent = (View) view.getParent();
        if (parent.getTag() != null && ((int) parent.getTag()) == 1337) {
            Bundle args = new Bundle();
            presentFragment(new QrActivity(args));
        }
    }

    private void killThatApp() {
        if (Build.VERSION.SDK_INT >= 21) {
            getParentActivity().finishAndRemoveTask();
        } else if (Build.VERSION.SDK_INT >= 16) {
            getParentActivity().finishAffinity();
        }
        System.exit(0);
    }

    private void showDSAlert(int labelId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Device spoofing");
        builder.setMessage(String.format("Override Device %s?", DSMainActivity.DS_LABEL[labelId]));
        builder.setNeutralButton("Global", (dialogInterface, i) -> {
            try {
                presentFragment(new DSMainActivity(labelId, 0, labelId));
            } catch (Exception durovrelogin) {
                durovrelogin.printStackTrace();
            }
        });
        builder.setPositiveButton("Currect account", (dialogInterface, i) -> {
            try {
                presentFragment(new DSMainActivity(labelId, 1, labelId));
            } catch (Exception durovrelogin) {
                durovrelogin.printStackTrace();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
        button = (TextView) dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
    }

    private void showDSResetGlobalAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Device spoofing");
        builder.setMessage("Reset global values to the vanilla ones?");
        builder.setPositiveButton("Yes!", (dialogInterface, i) -> {
            try {
                UserConfig.resetHmDevices();
                UserConfig.syncHmDevices();
                MessagesController.refreshGlobalTelegraherSettings();
            } catch (Exception durovrelogin) {
                durovrelogin.printStackTrace();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, TextDetailSettingsCell.class, TextSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return themeDescriptions;
    }
}

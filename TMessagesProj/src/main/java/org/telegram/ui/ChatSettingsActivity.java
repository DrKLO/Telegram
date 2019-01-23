/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.RecyclerListView;

public class ChatSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int appearanceRow;
    private int nightModeRow;
    private int themeRow;
    private int backgroundRow;
    private int textSizeRow;
    private int appearance2Row;
    private int settingsRow;
    private int customTabsRow;
    private int directShareRow;
    private int raiseToSpeakRow;
    private int sendByEnterRow;
    private int autoplayGifsRow;
    private int saveToGalleryRow;
    private int enableAnimationsRow;
    private int settings2Row;
    private int stickersRow;
    private int stickersSection2Row;

    private int emojiRow;
    private int contactsReimportRow;
    private int contactsSortRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        emojiRow = -1;
        contactsReimportRow = -1;
        contactsSortRow = -1;

        rowCount = 0;
        appearanceRow = rowCount++;
        nightModeRow = rowCount++;
        themeRow = rowCount++;
        backgroundRow = rowCount++;
        textSizeRow = rowCount++;
        appearance2Row = rowCount++;
        settingsRow = rowCount++;
        customTabsRow = rowCount++;
        directShareRow = rowCount++;
        enableAnimationsRow = rowCount++;
        raiseToSpeakRow = rowCount++;
        sendByEnterRow = rowCount++;
        autoplayGifsRow = rowCount++;
        saveToGalleryRow = rowCount++;
        settings2Row = rowCount++;
        stickersRow = rowCount++;
        stickersSection2Row = rowCount++;

        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("ChatSettings", R.string.ChatSettings));

        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        listView.setAdapter(listAdapter);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == textSizeRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("TextSize", R.string.TextSize));
                final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                numberPicker.setMinValue(12);
                numberPicker.setMaxValue(30);
                numberPicker.setValue(SharedConfig.fontSize);
                builder.setView(numberPicker);
                builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), (dialog, which) -> {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("fons_size", numberPicker.getValue());
                    SharedConfig.fontSize = numberPicker.getValue();
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                showDialog(builder.create());
            } else if (position == enableAnimationsRow) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean animations = preferences.getBoolean("view_animations", true);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("view_animations", !animations);
                editor.commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!animations);
                }
            } else if (position == backgroundRow) {
                presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
            } else if (position == sendByEnterRow) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean send = preferences.getBoolean("send_by_enter", false);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("send_by_enter", !send);
                editor.commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!send);
                }
            } else if (position == raiseToSpeakRow) {
                SharedConfig.toogleRaiseToSpeak();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.raiseToSpeak);
                }
            } else if (position == autoplayGifsRow) {
                SharedConfig.toggleAutoplayGifs();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.autoplayGifs);
                }
            } else if (position == saveToGalleryRow) {
                SharedConfig.toggleSaveToGallery();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.saveToGallery);
                }
            } else if (position == customTabsRow) {
                SharedConfig.toggleCustomTabs();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.customTabs);
                }
            } else if(position == directShareRow) {
                SharedConfig.toggleDirectShare();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.directShare);
                }
            } else if (position == themeRow) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            } else if (position == contactsReimportRow) {
                //not implemented
            } else if (position == contactsSortRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SortBy", R.string.SortBy));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("Default", R.string.Default),
                        LocaleController.getString("SortFirstName", R.string.SortFirstName),
                        LocaleController.getString("SortLastName", R.string.SortLastName)
                }, (dialog, which) -> {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("sortContactsBy", which);
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == stickersRow) {
                presentFragment(new StickersActivity(DataQuery.TYPE_IMAGE));
            } else if (position == emojiRow) {
                if (getParentActivity() == null) {
                    return;
                }
                final boolean maskValues[] = new boolean[2];
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());

                builder.setApplyTopPadding(false);
                builder.setApplyBottomPadding(false);
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                for (int a = 0; a < (Build.VERSION.SDK_INT >= 19 ? 2 : 1); a++) {
                    String name = null;
                    if (a == 0) {
                        maskValues[a] = SharedConfig.allowBigEmoji;
                        name = LocaleController.getString("EmojiBigSize", R.string.EmojiBigSize);
                    } else if (a == 1) {
                        maskValues[a] = SharedConfig.useSystemEmoji;
                        name = LocaleController.getString("EmojiUseDefault", R.string.EmojiUseDefault);
                    }
                    CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 1, 21);
                    checkBoxCell.setTag(a);
                    checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    checkBoxCell.setText(name, "", maskValues[a], true);
                    checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    checkBoxCell.setOnClickListener(v -> {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        int num = (Integer) cell.getTag();
                        maskValues[num] = !maskValues[num];
                        cell.setChecked(maskValues[num], true);
                    });
                }
                BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 1);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cell.setTextAndIcon(LocaleController.getString("Save", R.string.Save).toUpperCase(), 0);
                cell.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                cell.setOnClickListener(v -> {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                    editor.putBoolean("allowBigEmoji", SharedConfig.allowBigEmoji = maskValues[0]);
                    editor.putBoolean("useSystemEmoji", SharedConfig.useSystemEmoji = maskValues[1]);
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (position == nightModeRow) {
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                        checkCell.setChecked(true);
                    } else {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                        checkCell.setChecked(false);
                    }
                    Theme.saveAutoNightThemeConfig();
                    Theme.checkAutoNightThemeConditions();
                    boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                    String value = enabled ? Theme.getCurrentNightThemeName() : LocaleController.getString("NotificationsOff", R.string.NotificationsOff);
                    checkCell.setTextAndValueAndCheck(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), value, enabled, true);
                } else {
                    presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 2: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == textSizeRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        int size = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
                        textCell.setTextAndValue(LocaleController.getString("TextSize", R.string.TextSize), String.format("%d", size), false);
                    } else if (position == contactsSortRow) {
                        String value;
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        int sort = preferences.getInt("sortContactsBy", 0);
                        if (sort == 0) {
                            value = LocaleController.getString("Default", R.string.Default);
                        } else if (sort == 1) {
                            value = LocaleController.getString("FirstName", R.string.SortFirstName);
                        } else {
                            value = LocaleController.getString("LastName", R.string.SortLastName);
                        }
                        textCell.setTextAndValue(LocaleController.getString("SortBy", R.string.SortBy), value, true);
                    } else if (position == backgroundRow) {
                        textCell.setText(LocaleController.getString("ChatBackground", R.string.ChatBackground), true);
                    } else if (position == contactsReimportRow) {
                        textCell.setText(LocaleController.getString("ImportContacts", R.string.ImportContacts), true);
                    } else if (position == stickersRow) {
                        textCell.setText(LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), false);
                    } else if (position == emojiRow) {
                        textCell.setText(LocaleController.getString("Emoji", R.string.Emoji), true);
                    }
                    break;
                }
                case 3: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    if (position == enableAnimationsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("EnableAnimations", R.string.EnableAnimations), preferences.getBoolean("view_animations", true), true);
                    } else if (position == sendByEnterRow) {
                        textCell.setTextAndCheck(LocaleController.getString("SendByEnter", R.string.SendByEnter), preferences.getBoolean("send_by_enter", false), true);
                    } else if (position == saveToGalleryRow) {
                        textCell.setTextAndCheck(LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings), SharedConfig.saveToGallery, false);
                    } else if (position == autoplayGifsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AutoplayGifs", R.string.AutoplayGifs), SharedConfig.autoplayGifs, true);
                    } else if (position == raiseToSpeakRow) {
                        textCell.setTextAndCheck(LocaleController.getString("RaiseToSpeak", R.string.RaiseToSpeak), SharedConfig.raiseToSpeak, true);
                    } else if (position == customTabsRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("ChromeCustomTabs", R.string.ChromeCustomTabs), LocaleController.getString("ChromeCustomTabsInfo", R.string.ChromeCustomTabsInfo), SharedConfig.customTabs, false, true);
                    } else if (position == directShareRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("DirectShare", R.string.DirectShare), LocaleController.getString("DirectShareInfo", R.string.DirectShareInfo), SharedConfig.directShare, false, true);
                    }
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == settingsRow) {
                        headerCell.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                    } else if (position == appearanceRow) {
                        headerCell.setText(LocaleController.getString("Appearance", R.string.Appearance));
                    }
                    break;
                }
                case 5: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == nightModeRow) {
                        boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                        String value = enabled ? Theme.getCurrentNightThemeName() : LocaleController.getString("NotificationsOff", R.string.NotificationsOff);
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), value, enabled, true);
                    }
                    break;
                }
                case 6: {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) holder.itemView;
                    if (position == themeRow) {
                        textCell.setTextAndValue(LocaleController.getString("Theme", R.string.Theme), LocaleController.getString("ThemeInfo", R.string.ThemeInfo), true);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == textSizeRow || position == enableAnimationsRow || position == backgroundRow ||
                    position == sendByEnterRow || position == autoplayGifsRow || position == contactsSortRow ||
                    position == contactsReimportRow || position == saveToGalleryRow || position == stickersRow ||
                    position == raiseToSpeakRow || position == customTabsRow || position == directShareRow ||
                    position == emojiRow || position == themeRow || position == nightModeRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stickersSection2Row || position == settings2Row || position == appearance2Row) {
                return 1;
            } else if (position == backgroundRow || position == contactsReimportRow ||
                    position == textSizeRow || position == contactsSortRow || position == stickersRow ||
                    position == emojiRow) {
                return 2;
            } else if (position == enableAnimationsRow || position == sendByEnterRow || position == saveToGalleryRow ||
                    position == autoplayGifsRow || position == raiseToSpeakRow || position == customTabsRow ||
                    position == directShareRow) {
                return 3;
            } else if (position == appearanceRow || position == settingsRow) {
                return 4;
            } else if (position == nightModeRow) {
                return 5;
            }
            return 6;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{EmptyCell.class, TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_avatar_actionBarIconBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
        };
    }
}

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
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.MaxFileSizeCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.Collections;

public class DataAutoDownloadActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private ArrayList<DownloadController.Preset> presets = new ArrayList<>();
    private int selectedPreset = 1;
    private int currentPresetNum;

    private int currentType;

    private boolean animateChecked;

    private int autoDownloadRow;
    private int autoDownloadSectionRow;
    private int usageHeaderRow;
    private int usageProgressRow;
    private int usageSectionRow;
    private int typeHeaderRow;
    private int photosRow;
    private int videosRow;
    private int filesRow;
    private int typeSectionRow;

    private int rowCount;

    private DownloadController.Preset lowPreset;
    private DownloadController.Preset mediumPreset;
    private DownloadController.Preset highPreset;
    private DownloadController.Preset typePreset;
    private DownloadController.Preset defaultPreset;

    private boolean wereAnyChanges;

    private String key;
    private String key2;

    public DataAutoDownloadActivity(int type) {
        super();
        currentType = type;

        lowPreset = DownloadController.getInstance(currentAccount).lowPreset;
        mediumPreset = DownloadController.getInstance(currentAccount).mediumPreset;
        highPreset = DownloadController.getInstance(currentAccount).highPreset;

        if (currentType == 0) {
            currentPresetNum = DownloadController.getInstance(currentAccount).currentMobilePreset;
            typePreset = DownloadController.getInstance(currentAccount).mobilePreset;
            defaultPreset = mediumPreset;
            key = "mobilePreset";
            key2 = "currentMobilePreset";
        } else if (currentType == 1) {
            currentPresetNum = DownloadController.getInstance(currentAccount).currentWifiPreset;
            typePreset = DownloadController.getInstance(currentAccount).wifiPreset;
            defaultPreset = highPreset;
            key = "wifiPreset";
            key2 = "currentWifiPreset";
        } else {
            currentPresetNum = DownloadController.getInstance(currentAccount).currentRoamingPreset;
            typePreset = DownloadController.getInstance(currentAccount).roamingPreset;
            defaultPreset = lowPreset;
            key = "roamingPreset";
            key2 = "currentRoamingPreset";
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        fillPresets();
        updateRows();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("AutoDownloadOnMobileData", R.string.AutoDownloadOnMobileData));
        } else if (currentType == 1) {
            actionBar.setTitle(LocaleController.getString("AutoDownloadOnWiFiData", R.string.AutoDownloadOnWiFiData));
        } else if (currentType == 2) {
            actionBar.setTitle(LocaleController.getString("AutoDownloadOnRoamingData", R.string.AutoDownloadOnRoamingData));
        }
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(true);
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
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == autoDownloadRow) {
                if (currentPresetNum != 3) {
                    if (currentPresetNum == 0) {
                        typePreset.set(lowPreset);
                    } else if (currentPresetNum == 1) {
                        typePreset.set(mediumPreset);
                    } else if (currentPresetNum == 2) {
                        typePreset.set(highPreset);
                    }
                }

                TextCheckCell cell = (TextCheckCell) view;
                boolean checked = cell.isChecked();

                if (!checked && typePreset.enabled) {
                    System.arraycopy(defaultPreset.mask, 0, typePreset.mask, 0, 4);
                } else {
                    typePreset.enabled = !typePreset.enabled;
                }
                view.setTag(typePreset.enabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
                cell.setBackgroundColorAnimated(!checked, Theme.getColor(typePreset.enabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                updateRows();
                if (typePreset.enabled) {
                    listAdapter.notifyItemRangeInserted(autoDownloadSectionRow + 1, 8);
                } else {
                    listAdapter.notifyItemRangeRemoved(autoDownloadSectionRow + 1, 8);
                }
                listAdapter.notifyItemChanged(autoDownloadSectionRow);
                SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                editor.putString(key, typePreset.toString());
                editor.putInt(key2, currentPresetNum = 3);
                if (currentType == 0) {
                    DownloadController.getInstance(currentAccount).currentMobilePreset = currentPresetNum;
                } else if (currentType == 1) {
                    DownloadController.getInstance(currentAccount).currentWifiPreset = currentPresetNum;
                } else {
                    DownloadController.getInstance(currentAccount).currentRoamingPreset = currentPresetNum;
                }
                editor.commit();

                cell.setChecked(!checked);
                DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                wereAnyChanges = true;
            } else if (position == photosRow || position == videosRow || position == filesRow) {
                if (!view.isEnabled()) {
                    return;
                }
                int type;
                if (position == photosRow) {
                    type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
                } else if (position == videosRow) {
                    type = DownloadController.AUTODOWNLOAD_TYPE_VIDEO;
                } else {
                    type = DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT;
                }
                int index = DownloadController.typeToIndex(type);

                DownloadController.Preset currentPreset;
                String key;
                String key2;
                if (currentType == 0) {
                    currentPreset = DownloadController.getInstance(currentAccount).getCurrentMobilePreset();
                    key = "mobilePreset";
                    key2 = "currentMobilePreset";
                } else if (currentType == 1) {
                    currentPreset = DownloadController.getInstance(currentAccount).getCurrentWiFiPreset();
                    key = "wifiPreset";
                    key2 = "currentWifiPreset";
                } else {
                    currentPreset = DownloadController.getInstance(currentAccount).getCurrentRoamingPreset();
                    key = "roamingPreset";
                    key2 = "currentRoamingPreset";
                }

                NotificationsCheckCell cell = (NotificationsCheckCell) view;
                boolean checked = cell.isChecked();

                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {

                    if (currentPresetNum != 3) {
                        if (currentPresetNum == 0) {
                            typePreset.set(lowPreset);
                        } else if (currentPresetNum == 1) {
                            typePreset.set(mediumPreset);
                        } else if (currentPresetNum == 2) {
                            typePreset.set(highPreset);
                        }
                    }

                    boolean hasAny = false;
                    for (int a = 0; a < typePreset.mask.length; a++) {
                        if ((currentPreset.mask[a] & type) != 0) {
                            hasAny = true;
                            break;
                        }
                    }
                    for (int a = 0; a < typePreset.mask.length; a++) {
                        if (checked) {
                            typePreset.mask[a] &=~ type;
                        } else if (!hasAny) {
                            typePreset.mask[a] |= type;
                        }
                    }

                    SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                    editor.putString(key, typePreset.toString());
                    editor.putInt(key2, currentPresetNum = 3);
                    if (currentType == 0) {
                        DownloadController.getInstance(currentAccount).currentMobilePreset = currentPresetNum;
                    } else if (currentType == 1) {
                        DownloadController.getInstance(currentAccount).currentWifiPreset = currentPresetNum;
                    } else {
                        DownloadController.getInstance(currentAccount).currentRoamingPreset = currentPresetNum;
                    }
                    editor.commit();

                    cell.setChecked(!checked);
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, position);
                    }
                    DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                    wereAnyChanges = true;
                    fillPresets();
                } else {
                    if (getParentActivity() == null) {
                        return;
                    }
                    BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                    builder.setApplyTopPadding(false);
                    builder.setApplyBottomPadding(false);
                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setCustomView(linearLayout);

                    HeaderCell headerCell = new HeaderCell(getParentActivity(), Theme.key_dialogTextBlue2, 21, 15, false);
                    if (position == photosRow) {
                        headerCell.setText(LocaleController.getString("AutoDownloadPhotosTitle", R.string.AutoDownloadPhotosTitle));
                    } else if (position == videosRow) {
                        headerCell.setText(LocaleController.getString("AutoDownloadVideosTitle", R.string.AutoDownloadVideosTitle));
                    } else {
                        headerCell.setText(LocaleController.getString("AutoDownloadFilesTitle", R.string.AutoDownloadFilesTitle));
                    }
                    linearLayout.addView(headerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    final MaxFileSizeCell[] sizeCell = new MaxFileSizeCell[1];
                    final TextCheckCell[] checkCell = new TextCheckCell[1];
                    final AnimatorSet[] animatorSet = new AnimatorSet[1];

                    TextCheckBoxCell[] cells = new TextCheckBoxCell[4];
                    for (int a = 0; a < 4; a++) {
                        TextCheckBoxCell checkBoxCell = cells[a] = new TextCheckBoxCell(getParentActivity(), true, false);
                        if (a == 0) {
                            cells[a].setTextAndCheck(LocaleController.getString("AutodownloadContacts", R.string.AutodownloadContacts), (currentPreset.mask[DownloadController.PRESET_NUM_CONTACT] & type) != 0, true);
                        } else if (a == 1) {
                            cells[a].setTextAndCheck(LocaleController.getString("AutodownloadPrivateChats", R.string.AutodownloadPrivateChats), (currentPreset.mask[DownloadController.PRESET_NUM_PM] & type) != 0, true);
                        } else if (a == 2) {
                            cells[a].setTextAndCheck(LocaleController.getString("AutodownloadGroupChats", R.string.AutodownloadGroupChats), (currentPreset.mask[DownloadController.PRESET_NUM_GROUP] & type) != 0, true);
                        } else {
                            cells[a].setTextAndCheck(LocaleController.getString("AutodownloadChannels", R.string.AutodownloadChannels), (currentPreset.mask[DownloadController.PRESET_NUM_CHANNEL] & type) != 0, position != photosRow);
                        }
                        cells[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        cells[a].setOnClickListener(v -> {
                            if (!v.isEnabled()) {
                                return;
                            }
                            checkBoxCell.setChecked(!checkBoxCell.isChecked());
                            boolean hasAny = false;
                            for (int b = 0; b < cells.length; b++) {
                                if (cells[b].isChecked()) {
                                    hasAny = true;
                                    break;
                                }
                            }
                            if (position == videosRow && sizeCell[0].isEnabled() != hasAny) {
                                ArrayList<Animator> animators = new ArrayList<>();
                                sizeCell[0].setEnabled(hasAny, animators);
                                if (sizeCell[0].getSize() > 2 * 1024 * 1024) {
                                    checkCell[0].setEnabled(hasAny, animators);
                                }

                                if (animatorSet[0] != null) {
                                    animatorSet[0].cancel();
                                    animatorSet[0] = null;
                                }
                                animatorSet[0] = new AnimatorSet();
                                animatorSet[0].playTogether(animators);
                                animatorSet[0].addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        if (animator.equals(animatorSet[0])) {
                                            animatorSet[0] = null;
                                        }
                                    }
                                });
                                animatorSet[0].setDuration(150);
                                animatorSet[0].start();
                            }
                        });
                        linearLayout.addView(cells[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50));
                    }

                    if (position != photosRow) {
                        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(getParentActivity());

                        sizeCell[0] = new MaxFileSizeCell(getParentActivity()) {
                            @Override
                            protected void didChangedSizeValue(int value) {
                                if (position == videosRow) {
                                    infoCell.setText(LocaleController.formatString("AutoDownloadPreloadVideoInfo", R.string.AutoDownloadPreloadVideoInfo, AndroidUtilities.formatFileSize(value)));
                                    boolean enabled = value > 2 * 1024 * 1024;
                                    if (enabled != checkCell[0].isEnabled()) {
                                        ArrayList<Animator> animators = new ArrayList<>();
                                        checkCell[0].setEnabled(enabled, animators);

                                        if (animatorSet[0] != null) {
                                            animatorSet[0].cancel();
                                            animatorSet[0] = null;
                                        }
                                        animatorSet[0] = new AnimatorSet();
                                        animatorSet[0].playTogether(animators);
                                        animatorSet[0].addListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animator) {
                                                if (animator.equals(animatorSet[0])) {
                                                    animatorSet[0] = null;
                                                }
                                            }
                                        });
                                        animatorSet[0].setDuration(150);
                                        animatorSet[0].start();
                                    }
                                }
                            }
                        };
                        sizeCell[0].setSize(currentPreset.sizes[index]);
                        linearLayout.addView(sizeCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));

                        checkCell[0] = new TextCheckCell(getParentActivity(), 21, true);
                        linearLayout.addView(checkCell[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                        checkCell[0].setOnClickListener(v -> checkCell[0].setChecked(!checkCell[0].isChecked()));

                        Drawable drawable = Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                        CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                        combinedDrawable.setFullsize(true);
                        infoCell.setBackgroundDrawable(combinedDrawable);
                        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                        if (position == videosRow) {
                            sizeCell[0].setText(LocaleController.getString("AutoDownloadMaxVideoSize", R.string.AutoDownloadMaxVideoSize));
                            checkCell[0].setTextAndCheck(LocaleController.getString("AutoDownloadPreloadVideo", R.string.AutoDownloadPreloadVideo), currentPreset.preloadVideo, false);
                            infoCell.setText(LocaleController.formatString("AutoDownloadPreloadVideoInfo", R.string.AutoDownloadPreloadVideoInfo, AndroidUtilities.formatFileSize(currentPreset.sizes[index])));
                        } else {
                            sizeCell[0].setText(LocaleController.getString("AutoDownloadMaxFileSize", R.string.AutoDownloadMaxFileSize));
                            checkCell[0].setTextAndCheck(LocaleController.getString("AutoDownloadPreloadMusic", R.string.AutoDownloadPreloadMusic), currentPreset.preloadMusic, false);
                            infoCell.setText(LocaleController.getString("AutoDownloadPreloadMusicInfo", R.string.AutoDownloadPreloadMusicInfo));
                        }
                    } else {
                        sizeCell[0] = null;
                        checkCell[0] = null;

                        View divider = new View(getParentActivity());
                        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                        linearLayout.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    }
                    if (position == videosRow) {
                        boolean hasAny = false;
                        for (int b = 0; b < cells.length; b++) {
                            if (cells[b].isChecked()) {
                                hasAny = true;
                                break;
                            }
                        }
                        if (!hasAny) {
                            sizeCell[0].setEnabled(false, null);
                            checkCell[0].setEnabled(false, null);
                        }
                        if (currentPreset.sizes[index] <= 2 * 1024 * 1024) {
                            checkCell[0].setEnabled(false, null);
                        }
                    }

                    FrameLayout buttonsLayout = new FrameLayout(getParentActivity());
                    buttonsLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
                    linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

                    TextView textView = new TextView(getParentActivity());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                    textView.setGravity(Gravity.CENTER);
                    textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    textView.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
                    textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));
                    textView.setOnClickListener(v14 -> builder.getDismissRunnable().run());

                    textView = new TextView(getParentActivity());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                    textView.setGravity(Gravity.CENTER);
                    textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    textView.setText(LocaleController.getString("Save", R.string.Save).toUpperCase());
                    textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                    textView.setOnClickListener(v1 -> {
                        if (currentPresetNum != 3) {
                            if (currentPresetNum == 0) {
                                typePreset.set(lowPreset);
                            } else if (currentPresetNum == 1) {
                                typePreset.set(mediumPreset);
                            } else if (currentPresetNum == 2) {
                                typePreset.set(highPreset);
                            }
                        }

                        for (int a = 0; a < 4; a++) {
                            if (cells[a].isChecked()) {
                                typePreset.mask[a] |= type;
                            } else {
                                typePreset.mask[a] &= ~type;
                            }
                        }
                        if (sizeCell[0] != null) {
                            int size = (int) sizeCell[0].getSize();
                            typePreset.sizes[index] = (int) sizeCell[0].getSize();
                        }
                        if (checkCell[0] != null) {
                            if (position == videosRow) {
                                typePreset.preloadVideo = checkCell[0].isChecked();
                            } else {
                                typePreset.preloadMusic = checkCell[0].isChecked();
                            }
                        }
                        SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                        editor.putString(key, typePreset.toString());
                        editor.putInt(key2, currentPresetNum = 3);
                        if (currentType == 0) {
                            DownloadController.getInstance(currentAccount).currentMobilePreset = currentPresetNum;
                        } else if (currentType == 1) {
                            DownloadController.getInstance(currentAccount).currentWifiPreset = currentPresetNum;
                        } else {
                            DownloadController.getInstance(currentAccount).currentRoamingPreset = currentPresetNum;
                        }
                        editor.commit();
                        builder.getDismissRunnable().run();

                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                        if (holder != null) {
                            animateChecked = true;
                            listAdapter.onBindViewHolder(holder, position);
                            animateChecked = false;
                        }
                        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                        wereAnyChanges = true;
                        fillPresets();
                    });
                    showDialog(builder.create());
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

    @Override
    public void onPause() {
        super.onPause();
        if (wereAnyChanges) {
            DownloadController.getInstance(currentAccount).savePresetToServer(currentType);
            wereAnyChanges = false;
        }
    }

    private void fillPresets() {
        presets.clear();
        presets.add(lowPreset);
        presets.add(mediumPreset);
        presets.add(highPreset);
        if (!typePreset.equals(lowPreset) && !typePreset.equals(mediumPreset) && !typePreset.equals(highPreset)) {
            presets.add(typePreset);
        }
        Collections.sort(presets, (o1, o2) -> {
            int index1 = DownloadController.typeToIndex(DownloadController.AUTODOWNLOAD_TYPE_VIDEO);
            int index2 = DownloadController.typeToIndex(DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT);
            boolean video1 = false;
            boolean doc1 = false;
            for (int a = 0; a < o1.mask.length; a++) {
                if ((o1.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                    video1 = true;
                }
                if ((o1.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                    doc1 = true;
                }
                if (video1 && doc1) {
                    break;
                }
            }
            boolean video2 = false;
            boolean doc2 = false;
            for (int a = 0; a < o2.mask.length; a++) {
                if ((o2.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                    video2 = true;
                }
                if ((o2.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                    doc2 = true;
                }
                if (video2 && doc2) {
                    break;
                }
            }
            long size1 = (video1 ? o1.sizes[index1] : 0) + (doc1 ? o1.sizes[index2] : 0);
            long size2 = (video2 ? o2.sizes[index1] : 0) + (doc2 ? o2.sizes[index2] : 0);
            if (size1 > size2) {
                return 1;
            } else if (size1 < size2) {
                return -1;
            }
            return 0;
        });
        if (currentPresetNum == 0 || currentPresetNum == 3 && typePreset.equals(lowPreset)) {
            selectedPreset = presets.indexOf(lowPreset);
        } else if (currentPresetNum == 1 || currentPresetNum == 3 && typePreset.equals(mediumPreset)) {
            selectedPreset = presets.indexOf(mediumPreset);
        } else if (currentPresetNum == 2 || currentPresetNum == 3 && typePreset.equals(highPreset)) {
            selectedPreset = presets.indexOf(highPreset);
        } else {
            selectedPreset = presets.indexOf(typePreset);
        }
        if (listView != null) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(usageProgressRow);
            if (holder != null && holder.itemView instanceof SlideChooseView) {
                updatePresetChoseView((SlideChooseView) holder.itemView);
            } else {
                listAdapter.notifyItemChanged(usageProgressRow);
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        autoDownloadRow = rowCount++;
        autoDownloadSectionRow = rowCount++;
        if (typePreset.enabled) {
            usageHeaderRow = rowCount++;
            usageProgressRow = rowCount++;
            usageSectionRow = rowCount++;
            typeHeaderRow = rowCount++;
            photosRow = rowCount++;
            videosRow = rowCount++;
            filesRow = rowCount++;
            typeSectionRow = rowCount++;
        } else {
            usageHeaderRow = -1;
            usageProgressRow = -1;
            usageSectionRow = -1;
            typeHeaderRow = -1;
            photosRow = -1;
            videosRow = -1;
            filesRow = -1;
            typeSectionRow = -1;
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
                case 0: {
                    TextCheckCell view = (TextCheckCell) holder.itemView;
                    if (position == autoDownloadRow) {
                        view.setDrawCheckRipple(true);
                        view.setTextAndCheck(LocaleController.getString("AutoDownloadMedia", R.string.AutoDownloadMedia), typePreset.enabled, false);
                        view.setTag(typePreset.enabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
                        view.setBackgroundColor(Theme.getColor(typePreset.enabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                    }
                    break;
                }
                case 2: {
                    HeaderCell view = (HeaderCell) holder.itemView;
                    if (position == usageHeaderRow) {
                        view.setText(LocaleController.getString("AutoDownloadDataUsage", R.string.AutoDownloadDataUsage));
                    } else if (position == typeHeaderRow) {
                        view.setText(LocaleController.getString("AutoDownloadTypes", R.string.AutoDownloadTypes));
                    }
                    break;
                }
                case 3: {
                    SlideChooseView slideChooseView = (SlideChooseView) holder.itemView;
                    updatePresetChoseView(slideChooseView);
                    break;
                }
                case 4: {
                    NotificationsCheckCell view = (NotificationsCheckCell) holder.itemView;
                    DownloadController.Preset preset;
                    String text;
                    int type;
                    if (position == photosRow) {
                        text = LocaleController.getString("AutoDownloadPhotos", R.string.AutoDownloadPhotos);
                        type = DownloadController.AUTODOWNLOAD_TYPE_PHOTO;
                    } else if (position == videosRow) {
                        text = LocaleController.getString("AutoDownloadVideos", R.string.AutoDownloadVideos);
                        type = DownloadController.AUTODOWNLOAD_TYPE_VIDEO;
                    } else {
                        text = LocaleController.getString("AutoDownloadFiles", R.string.AutoDownloadFiles);
                        type = DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT;
                    }
                    if (currentType == 0) {
                        preset = DownloadController.getInstance(currentAccount).getCurrentMobilePreset();
                    } else if (currentType == 1) {
                        preset = DownloadController.getInstance(currentAccount).getCurrentWiFiPreset();
                    } else {
                        preset = DownloadController.getInstance(currentAccount).getCurrentRoamingPreset();
                    }
                    long maxSize = preset.sizes[DownloadController.typeToIndex(type)];

                    int count = 0;
                    StringBuilder builder = new StringBuilder();
                    for (int a = 0; a < preset.mask.length; a++) {
                        if ((preset.mask[a] & type) != 0) {
                            if (builder.length() != 0) {
                                builder.append(", ");
                            }
                            switch (a) {
                                case 0:
                                    builder.append(LocaleController.getString("AutoDownloadContacts", R.string.AutoDownloadContacts));
                                    break;
                                case 1:
                                    builder.append(LocaleController.getString("AutoDownloadPm", R.string.AutoDownloadPm));
                                    break;
                                case 2:
                                    builder.append(LocaleController.getString("AutoDownloadGroups", R.string.AutoDownloadGroups));
                                    break;
                                case 3:
                                    builder.append(LocaleController.getString("AutoDownloadChannels", R.string.AutoDownloadChannels));
                                    break;
                            }
                            count++;
                        }
                    }
                    if (count == 4) {
                        builder.setLength(0);
                        if (position == photosRow) {
                            builder.append(LocaleController.getString("AutoDownloadOnAllChats", R.string.AutoDownloadOnAllChats));
                        } else {
                            builder.append(LocaleController.formatString("AutoDownloadUpToOnAllChats", R.string.AutoDownloadUpToOnAllChats, AndroidUtilities.formatFileSize(maxSize)));
                        }
                    } else if (count == 0) {
                        builder.append(LocaleController.getString("AutoDownloadOff", R.string.AutoDownloadOff));
                    } else {
                        if (position == photosRow) {
                            builder = new StringBuilder(LocaleController.formatString("AutoDownloadOnFor", R.string.AutoDownloadOnFor, builder.toString()));
                        } else {
                            builder = new StringBuilder(LocaleController.formatString("AutoDownloadOnUpToFor", R.string.AutoDownloadOnUpToFor, AndroidUtilities.formatFileSize(maxSize), builder.toString()));
                        }
                    }
                    if (animateChecked) {
                        view.setChecked(count != 0);
                    }
                    view.setTextAndValueAndCheck(text, builder, count != 0, 0, true, position != filesRow);
                    break;
                }
                case 5: {
                    TextInfoPrivacyCell view = (TextInfoPrivacyCell) holder.itemView;
                    if (position == typeSectionRow) {
                        view.setText(LocaleController.getString("AutoDownloadAudioInfo", R.string.AutoDownloadAudioInfo));
                        view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        view.setFixedSize(0);
                        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    } else if (position == autoDownloadSectionRow) {
                        if (usageHeaderRow == -1) {
                            view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            if (currentType == 0) {
                                view.setText(LocaleController.getString("AutoDownloadOnMobileDataInfo", R.string.AutoDownloadOnMobileDataInfo));
                            } else if (currentType == 1) {
                                view.setText(LocaleController.getString("AutoDownloadOnWiFiDataInfo", R.string.AutoDownloadOnWiFiDataInfo));
                            } else if (currentType == 2) {
                                view.setText(LocaleController.getString("AutoDownloadOnRoamingDataInfo", R.string.AutoDownloadOnRoamingDataInfo));
                            }
                            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                        } else {
                            view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                            view.setText(null);
                            view.setFixedSize(12);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                            } else {
                                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                            }
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == photosRow || position == videosRow || position == filesRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    TextCheckCell cell = new TextCheckCell(mContext);
                    cell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    cell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    cell.setHeight(56);
                    view = cell;
                    break;
                }
                case 1: {
                    view = new ShadowSectionCell(mContext);
                    break;
                }
                case 2: {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 3: {
                    SlideChooseView slideChooseView = new SlideChooseView(mContext);
                    view = slideChooseView;
                    slideChooseView.setCallback(index -> {
                        DownloadController.Preset preset = presets.get(index);
                        if (preset == lowPreset) {
                            currentPresetNum = 0;
                        } else if (preset == mediumPreset) {
                            currentPresetNum = 1;
                        } else if (preset == highPreset) {
                            currentPresetNum = 2;
                        } else {
                            currentPresetNum = 3;
                        }
                        if (currentType == 0) {
                            DownloadController.getInstance(currentAccount).currentMobilePreset = currentPresetNum;
                        } else if (currentType == 1) {
                            DownloadController.getInstance(currentAccount).currentWifiPreset = currentPresetNum;
                        } else {
                            DownloadController.getInstance(currentAccount).currentRoamingPreset = currentPresetNum;
                        }
                        SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                        editor.putInt(key2, currentPresetNum);
                        editor.commit();
                        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                        for (int a = 0; a < 3; a++) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(photosRow + a);
                            if (holder != null) {
                                listAdapter.onBindViewHolder(holder, photosRow + a);
                            }
                        }
                        wereAnyChanges = true;
                    });
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 4: {
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 5:
                default: {
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == autoDownloadRow) {
                return 0;
            } else if (position == usageSectionRow) {
                return 1;
            } else if (position == usageHeaderRow || position == typeHeaderRow) {
                return 2;
            } else if (position == usageProgressRow) {
                return 3;
            } else if (position == photosRow || position == videosRow || position == filesRow) {
                return 4;
            } else {
                return 5;
            }
        }
    }

    private void updatePresetChoseView(SlideChooseView slideChooseView) {
        String[] presetsStr = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            DownloadController.Preset preset = presets.get(i);
            if (preset == lowPreset) {
                presetsStr[i] = LocaleController.getString("AutoDownloadLow", R.string.AutoDownloadLow);
            } else if (preset == mediumPreset) {
                presetsStr[i] = LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium);
            } else if (preset == highPreset) {
                presetsStr[i] = LocaleController.getString("AutoDownloadHigh", R.string.AutoDownloadHigh);
            } else {
                presetsStr[i] = LocaleController.getString("AutoDownloadCustom", R.string.AutoDownloadCustom);
            }
        }
        slideChooseView.setOptions(selectedPreset, presetsStr);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, NotificationsCheckCell.class, SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundChecked));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundUnchecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundCheckText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlue));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlueChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlueThumb));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlueThumbChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlueSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackBlueSelectorChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        return themeDescriptions;
    }
}

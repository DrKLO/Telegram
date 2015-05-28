/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorSelectorDialog;
import org.telegram.ui.Components.NumberPicker;

import static org.telegram.ui.Components.ColorSelectorDialog.OnColorChangedListener;

public class ThemingDrawerActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private int headerSection2Row;
    private int headerColorRow;
    private int headerBackgroundCheckRow;
    private int hideBackgroundShadowRow;
    private int rowsSectionRow;
    private int rowsSection2Row;
    private int listColorRow;
    private int avatarColorRow;
    private int avatarRadiusRow;
    private int nameColorRow;
    private int nameSizeRow;
    private int phoneColorRow;
    private int phoneSizeRow;
    private int iconColorRow;
    private int optionColorRow;
    private int optionSizeRow;
    private int versionColorRow;
    private int versionSizeRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerBackgroundCheckRow = rowCount++;
        hideBackgroundShadowRow = rowCount++;
        headerColorRow = rowCount++;
        avatarColorRow  = rowCount++;
        avatarRadiusRow  = rowCount++;
        nameColorRow = rowCount++;
        nameSizeRow = rowCount++;
        phoneColorRow = rowCount++;
        phoneSizeRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        listColorRow = rowCount++;
        iconColorRow = rowCount++;
        optionColorRow = rowCount++;
        optionSizeRow = rowCount++;
        versionColorRow  = rowCount++;
        versionSizeRow  = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

    }

    @Override
    public View createView(Context context, LayoutInflater inflater) {
        if (fragmentView == null) {

            actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);

            if (AndroidUtilities.isTablet()) {
                actionBar.setOccupyStatusBar(false);
            }
            actionBar.setTitle(LocaleController.getString("NavigationDrawer", R.string.NavigationDrawer));

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
            FrameLayout frameLayout = (FrameLayout) fragmentView;

            listView = new ListView(context);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setListViewEdgeEffectColor(listView, AvatarDrawable.getProfileBackColorForId(5));
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {

                    SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                    final String key = view.getTag() != null ? view.getTag().toString() : "";

                    if (i == headerColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerHeaderColor", color);
                            }

                        },themePrefs.getInt("drawerHeaderColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == headerBackgroundCheckRow) {
                        boolean b = themePrefs.getBoolean( key, true);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }
                        if (listView != null) {
                            listView.invalidateViews();
                        }
                    } else if (i == hideBackgroundShadowRow) {
                        boolean b = themePrefs.getBoolean( key, true);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }
                        if (listView != null) {
                            listView.invalidateViews();
                        }
                    } else if (i == listColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerListColor", color);
                            }

                        },themePrefs.getInt("drawerListColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == iconColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerIconColor", color);

                            }

                        },themePrefs.getInt("drawerIconColor", 0xff737373), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == optionColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerOptionColor", color);
                            }

                        },themePrefs.getInt("drawerOptionColor", 0xff444444), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == versionColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerVersionColor", color);
                            }

                        },themePrefs.getInt("drawerVersionColor", 0xffa3a3a3), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == avatarColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerAvatarColor", color);
                            }

                        },themePrefs.getInt("drawerAvatarColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15)), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == nameColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerNameColor", color);
                            }

                        },themePrefs.getInt("drawerNameColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == phoneColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerPhoneColor", color);
                            }

                        },themePrefs.getInt("drawerPhoneColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == avatarRadiusRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarRadius", R.string.AvatarRadius));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerAvatarRadius", 32);
                        numberPicker.setMinValue(1);
                        numberPicker.setMaxValue(32);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("drawerAvatarRadius", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    } else if (i == nameSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("OwnNameSize", R.string.OwnNameSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerNameSize", 15);
                        numberPicker.setMinValue(10);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("drawerNameSize", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    } else if (i == phoneSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("PhoneSize", R.string.StatusSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerPhoneSize", 13);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(18);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("drawerPhoneSize", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    } else if (i == optionSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("OptionSize", R.string.OptionSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerOptionSize", 15);
                        numberPicker.setMinValue(10);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("drawerOptionSize", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    } else if (i == versionSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("VersionSize", R.string.VersionSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerVersionSize", 13);
                        numberPicker.setMinValue(10);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("drawerVersionSize", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (i == headerColorRow) {
                        resetInt("drawerHeaderColor");
                    } else if (i == listColorRow) {
                        resetInt("drawerListColor");
                    } else if (i == avatarColorRow) {
                        resetInt("drawerAvatarColor");
                    } else if (i == avatarRadiusRow) {
                        resetInt("drawerAvatarRadius");
                    } else if (i == nameColorRow) {
                        resetInt("drawerNameColor");
                    } else if (i == nameSizeRow) {
                        resetInt("drawerNameSize");
                    } else if (i == phoneColorRow) {
                        resetInt("drawerPhoneColor");
                    } else if (i == phoneSizeRow) {
                        resetInt("drawerPhoneSize");
                    } else if (i == iconColorRow) {
                        resetInt("drawerIconColor");
                    } else if (i == optionColorRow) {
                        resetInt("drawerOptionColor");
                    } else if (i == optionSizeRow) {
                        resetInt("drawerOptionSize");
                    } else if (i == versionColorRow) {
                        resetInt("drawerVersionColor");
                    } else if (i == versionSizeRow) {
                        resetInt("drawerVersionSize");
                    } else{
                        if(view.getTag() != null){
                            resetPref(view.getTag().toString());
                        }
                    }
                    return true;
                }
            });

            frameLayout.addView(actionBar);
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void resetPref(String key){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.commit();
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private void resetInt(String key){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.commit();
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private void commitInt(String key, int value){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(key, value);
        editor.commit();
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    //needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return false;
            }
        });
        listView.setAdapter(listAdapter);
        //actionBar.setBackgroundColor(AndroidUtilities.getIntColor("themeColor"));
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return  i == headerColorRow || i == headerBackgroundCheckRow || i == hideBackgroundShadowRow || i == listColorRow || i == iconColorRow || i == optionColorRow || i == optionSizeRow || i == avatarColorRow || i == avatarRadiusRow || i == nameColorRow || i == nameSizeRow || i == phoneColorRow || i == phoneSizeRow ||
                    i == versionColorRow || i == versionSizeRow;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            if (type == 0) {
                if (view == null) {
                    view = new ShadowSectionCell(mContext);
                }
            }
            else if (type == 1) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == headerSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("Header", R.string.Header));
                } else if (i == rowsSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("OptionsList", R.string.OptionsList));
                }
            }
            else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == avatarRadiusRow) {
                    int size = themePrefs.getInt("drawerAvatarRadius", AndroidUtilities.isTablet() ? 35 : 32);
                    textCell.setTextAndValue(LocaleController.getString("AvatarRadius", R.string.AvatarRadius), String.format("%d", size), true);
                } else if (i == nameSizeRow) {
                    int size = themePrefs.getInt("drawerNameSize", AndroidUtilities.isTablet() ? 17 : 15);
                    textCell.setTextAndValue(LocaleController.getString("OwnNameSize", R.string.OwnNameSize), String.format("%d", size), true);
                } else if (i == phoneSizeRow) {
                    int size = themePrefs.getInt("drawerPhoneSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("PhoneSize", R.string.PhoneSize), String.format("%d", size), true);
                } else if (i == optionSizeRow) {
                    int size = themePrefs.getInt("drawerOptionSize", AndroidUtilities.isTablet() ? 17 : 15);
                    textCell.setTextAndValue(LocaleController.getString("OptionSize", R.string.OptionSize), String.format("%d", size), true);
                } else if (i == versionSizeRow) {
                    int size = themePrefs.getInt("drawerVersionSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("VersionSize", R.string.VersionSize), String.format("%d", size), true);
                }

            }
            else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("drawerHeaderColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == listColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ListColor", R.string.ListColor), themePrefs.getInt("drawerListColor", 0xffffffff), true);
                } else if (i == iconColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("IconColor", R.string.IconColor), themePrefs.getInt("drawerIconColor", 0xff737373), true);
                } else if (i == optionColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("OptionColor", R.string.OptionColor), themePrefs.getInt("drawerOptionColor", 0xff444444), true);
                } else if (i == versionColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("VersionColor", R.string.VersionColor), themePrefs.getInt("drawerVersionColor", 0xffa3a3a3), true);
                } else if (i == avatarColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("AvatarColor", R.string.AvatarColor), themePrefs.getInt("drawerAvatarColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15)), true);
                } else if (i == nameColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("drawerNameColor", 0xffffffff), true);
                } else if (i == phoneColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("PhoneColor", R.string.PhoneColor), themePrefs.getInt("drawerPhoneColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), true);
                }
            }  else if (type == 4) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                }
                TextCheckCell textCell = (TextCheckCell) view;
                if (i == headerBackgroundCheckRow) {
                    textCell.setTag("drawerHeaderBGCheck");
                    textCell.setTextAndCheck(LocaleController.getString("HideBackground", R.string.HideBackground), themePrefs.getBoolean("drawerHeaderBGCheck", false), true);
                } else if (i == hideBackgroundShadowRow) {
                    textCell.setTag("drawerHideBGShadowCheck");
                    textCell.setTextAndCheck(LocaleController.getString("HideBackgroundShadow", R.string.HideBackgroundShadow), themePrefs.getBoolean("drawerHideBGShadowCheck", false), true);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if ( i == rowsSectionRow ) {
                return 0;
            }
            else if ( i == headerSection2Row || i == rowsSection2Row ) {
                return 1;
            }
            else if ( i == avatarRadiusRow || i == nameSizeRow ||  i == phoneSizeRow ||  i == optionSizeRow ||  i == versionSizeRow) {
                return 2;
            }
            else if ( i == headerColorRow || i == listColorRow || i == iconColorRow || i == optionColorRow || i == versionColorRow  || i == avatarColorRow  || i == nameColorRow || i == phoneColorRow) {
                return 3;
            }
            else if (i == headerBackgroundCheckRow || i == hideBackgroundShadowRow) {
                return 4;
            }
            else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}

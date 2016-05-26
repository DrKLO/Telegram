/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ColorSelectorDialog;
import org.telegram.ui.Components.NumberPicker;

import java.util.ArrayList;
import java.util.List;

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
    private int avatarSizeRow;
    private int listDividerColorRow;
    private int centerAvatarRow;

    private int rowGradientRow;
    private int rowGradientColorRow;
    private int rowGradientListCheckRow;
    private int headerGradientRow;
    private int headerGradientColorRow;

    private int rowCount;

    public final static int CENTER = 0;

    private boolean player = false;
    private boolean drawer = false;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerBackgroundCheckRow = rowCount++;
        hideBackgroundShadowRow = rowCount++;
        headerColorRow = rowCount++;
        headerGradientRow = rowCount++;
        headerGradientColorRow = rowCount++;
        avatarColorRow  = rowCount++;
        avatarRadiusRow  = rowCount++;
        avatarSizeRow = rowCount++;
        nameColorRow = rowCount++;
        nameSizeRow = rowCount++;
        phoneColorRow = rowCount++;
        phoneSizeRow = rowCount++;
        centerAvatarRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        listColorRow = rowCount++;
        rowGradientRow = rowCount++;
        rowGradientColorRow = rowCount++;
        //rowGradientListCheckRow = rowCount++;
        listDividerColorRow = rowCount++;
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
        if(player){
            if(MediaController.getInstance().getPlayingMessageObject() != null)NotificationCenter.getInstance().postNotificationName(NotificationCenter.audioPlayStateChanged, MediaController.getInstance().getPlayingMessageObject().getId());
        }
        if(drawer){
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
        }
    }

    @Override
    public View createView(Context context) {
        if (fragmentView == null) {

            //actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
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
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            listView.setBackgroundColor(preferences.getInt("prefBGColor", 0xffffffff));
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            int def = preferences.getInt("themeColor", AndroidUtilities.defColor);
            int hColor = preferences.getInt("prefHeaderColor", def);
            AndroidUtilities.setListViewEdgeEffectColor(listView, /*AvatarDrawable.getProfileBackColorForId(5)*/ hColor);
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
                    int defColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);

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

                        },themePrefs.getInt("drawerHeaderColor", defColor), CENTER, 0, false);
                        colorDialog.show();
                    }  else if (i == headerGradientRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("RowGradient", R.string.RowGradient));
                        List<CharSequence> array = new ArrayList<>();
                        array.add( LocaleController.getString("RowGradientDisabled", R.string.RowGradientDisabled));
                        array.add(LocaleController.getString("RowGradientTopBottom", R.string.RowGradientTopBottom));
                        array.add( LocaleController.getString("RowGradientLeftRight", R.string.RowGradientLeftRight));
                        array.add( LocaleController.getString("RowGradientTLBR", R.string.RowGradientTLBR));
                        array.add( LocaleController.getString("RowGradientBLTR", R.string.RowGradientBLTR));
                        String[] simpleArray = new String[ array.size() ];
                        array.toArray( new String[ array.size() ]);
                        builder.setItems(array.toArray(simpleArray), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                                themePrefs.edit().putInt("drawerHeaderGradient", which).commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else if (i == headerGradientColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerHeaderGradientColor", color);
                            }
                        },themePrefs.getInt("drawerHeaderGradientColor", defColor), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerBackgroundCheckRow) {
                        boolean b = themePrefs.getBoolean( key, false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }

                    } else if (i == hideBackgroundShadowRow) {
                        boolean b = themePrefs.getBoolean( key, false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }

                    } else if (i == centerAvatarRow) {
                        boolean b = themePrefs.getBoolean( key, false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
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
                                player = true;
                            }

                        },themePrefs.getInt("drawerListColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == rowGradientColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerRowGradientColor", color);
                            }
                        },themePrefs.getInt( "drawerRowGradientColor", 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == rowGradientListCheckRow) {
                        boolean b = themePrefs.getBoolean( "drawerRowGradientListCheck", false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean("drawerRowGradientListCheck", !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }

                    } else if (i == rowGradientRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("RowGradient", R.string.RowGradient));
                        List<CharSequence> array = new ArrayList<>();
                        array.add( LocaleController.getString("RowGradientDisabled", R.string.RowGradientDisabled));
                        array.add(LocaleController.getString("RowGradientTopBottom", R.string.RowGradientTopBottom));
                        array.add( LocaleController.getString("RowGradientLeftRight", R.string.RowGradientLeftRight));
                        array.add( LocaleController.getString("RowGradientTLBR", R.string.RowGradientTLBR));
                        array.add( LocaleController.getString("RowGradientBLTR", R.string.RowGradientBLTR));
                        String[] simpleArray = new String[ array.size() ];
                        array.toArray( new String[ array.size() ]);
                        builder.setItems(array.toArray(simpleArray), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                                themePrefs.edit().putInt("drawerRowGradient", which).commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    } else if (i == listDividerColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("drawerListDividerColor", color);
                                player = true;
                            }

                        },themePrefs.getInt("drawerListDividerColor", 0xffd9d9d9), CENTER, 0, true);
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
                                player = true;

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
                                player = true;
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

                        },themePrefs.getInt("drawerVersionColor", 0xffa3a3a3), CENTER, 0, true);
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

                        },themePrefs.getInt("drawerNameColor", 0xffffffff), CENTER, 0, true);
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

                        },themePrefs.getInt("drawerPhoneColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), CENTER, 0, true);

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
                    } else if (i == avatarSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarSize", R.string.AvatarSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("drawerAvatarSize", 64);
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(75);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("drawerAvatarSize", numberPicker.getValue());
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
                    drawer = true;
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
                    } else if (i == headerGradientRow) {
                        resetInt("drawerHeaderGradient");
                    } else if (i == headerGradientColorRow) {
                        resetInt("drawerHeaderGradientColor");
                    } else if (i == listColorRow) {
                        resetInt("drawerListColor");
                        player = true;
                    } else if (i == rowGradientColorRow) {
                        resetInt("drawerRowGradientColor");
                        player = true;
                    } else if (i == rowGradientRow) {
                        resetInt("drawerRowGradient");
                        player = true;
                    } else if (i == rowGradientListCheckRow) {
                        resetInt("drawerRowGradientListCheck");
                        player = true;
                    } else if (i == listDividerColorRow) {
                        resetInt("drawerListDividerColor");
                    } else if (i == avatarColorRow) {
                        resetInt("drawerAvatarColor");
                    } else if (i == avatarRadiusRow) {
                        resetInt("drawerAvatarRadius");
                    } else if (i == nameColorRow) {
                        resetInt("drawerNameColor");
                    } else if (i == avatarSizeRow) {
                        resetInt("drawerAvatarSize");
                    } else if (i == nameSizeRow) {
                        resetInt("drawerNameSize");
                    } else if (i == phoneColorRow) {
                        resetInt("drawerPhoneColor");
                    } else if (i == phoneSizeRow) {
                        resetInt("drawerPhoneSize");
                    } else if (i == iconColorRow) {
                        resetInt("drawerIconColor");
                        player = true;
                    } else if (i == optionColorRow) {
                        resetInt("drawerOptionColor");
                        player = true;
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
                    drawer = true;

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
        updateTheme();
        fixLayout();
    }

    private void updateTheme(){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int def = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
        actionBar.setBackgroundColor(themePrefs.getInt("prefHeaderColor", def));
        actionBar.setTitleColor(themePrefs.getInt("prefHeaderTitleColor", 0xffffffff));

        Drawable back = getParentActivity().getResources().getDrawable(R.drawable.ic_ab_back);
        back.setColorFilter(themePrefs.getInt("prefHeaderIconsColor", 0xffffffff), PorterDuff.Mode.MULTIPLY);
        actionBar.setBackButtonDrawable(back);
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
        if(drawer){

        }
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
            int h = AndroidUtilities.getIntDef("drawerHeaderGradient", 0);
            int g = AndroidUtilities.getIntDef("drawerRowGradient", 0);
            return  i == headerColorRow || i == headerGradientRow || h > 0 && i == headerGradientColorRow || i == headerBackgroundCheckRow || i == hideBackgroundShadowRow || i == centerAvatarRow ||
                    i == listColorRow || i == rowGradientRow || g != 0 &&  i == rowGradientColorRow || g != 0 && i == rowGradientListCheckRow || i == listDividerColorRow || i == iconColorRow || i == optionColorRow || i == optionSizeRow || i == avatarColorRow || i == avatarRadiusRow || i == nameColorRow || i == avatarSizeRow || i == nameSizeRow || i == phoneColorRow || i == phoneSizeRow ||
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
            int defColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
            int darkColor = AndroidUtilities.getIntDarkerColor("themeColor", 0x15);
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
                } else if (i == avatarSizeRow) {
                    int size = themePrefs.getInt("drawerAvatarSize", AndroidUtilities.isTablet() ? 68 : 64);
                    textCell.setTextAndValue(LocaleController.getString("AvatarSize", R.string.AvatarSize), String.format("%d", size), true);
                } else if (i == nameSizeRow) {
                    int size = themePrefs.getInt("drawerNameSize", AndroidUtilities.isTablet() ? 17 : 15);
                    textCell.setTextAndValue(LocaleController.getString("OwnNameSize", R.string.OwnNameSize), String.format("%d", size), true);
                } else if (i == optionSizeRow) {
                    int size = themePrefs.getInt("drawerOptionSize", AndroidUtilities.isTablet() ? 17 : 15);
                    textCell.setTextAndValue(LocaleController.getString("OptionSize", R.string.OptionSize), String.format("%d", size), true);
                } else if (i == phoneSizeRow) {
                    int size = themePrefs.getInt("drawerPhoneSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("PhoneSize", R.string.PhoneSize), String.format("%d", size), true);
                } else if (i == versionSizeRow) {
                    int size = themePrefs.getInt("drawerVersionSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("VersionSize", R.string.VersionSize), String.format("%d", size), false);
                }

            }
            else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("drawerHeaderColor", defColor), false);
                } else if (i == headerGradientColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RowGradientColor", R.string.RowGradientColor), themePrefs.getInt("drawerHeaderGradient", 0) == 0 ? 0x00000000 : themePrefs.getInt("drawerHeaderGradientColor", defColor), true);
                } else if (i == listColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ListColor", R.string.ListColor), themePrefs.getInt("drawerListColor", 0xffffffff), false);
                } else if (i == rowGradientColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RowGradientColor", R.string.RowGradientColor), themePrefs.getInt("drawerRowGradient", 0) == 0 ? 0x00000000 : themePrefs.getInt("drawerRowGradientColor", 0xffffffff), true);
                } else if (i == listDividerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ListDividerColor", R.string.ListDividerColor), themePrefs.getInt("drawerListDividerColor", 0xffd9d9d9), true);
                } else if (i == iconColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("IconColor", R.string.IconColor), themePrefs.getInt("drawerIconColor", 0xff737373), true);
                } else if (i == optionColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("OptionColor", R.string.OptionColor), themePrefs.getInt("drawerOptionColor", 0xff444444), true);
                } else if (i == versionColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("VersionColor", R.string.VersionColor), themePrefs.getInt("drawerVersionColor", 0xffa3a3a3), true);
                } else if (i == avatarColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("AvatarColor", R.string.AvatarColor), themePrefs.getInt("drawerAvatarColor", darkColor), true);
                } else if (i == nameColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("drawerNameColor", 0xffffffff), true);
                } else if (i == phoneColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("PhoneColor", R.string.PhoneColor), themePrefs.getInt("drawerPhoneColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), true);
                }
            } else if (type == 4) {
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
                } else if (i == centerAvatarRow) {
                    textCell.setTag("drawerCenterAvatarCheck");
                    textCell.setTextAndCheck(LocaleController.getString("CenterAvatar", R.string.CenterAvatar), themePrefs.getBoolean("drawerCenterAvatarCheck", false), false);
                } else if (i == rowGradientListCheckRow) {
                    textCell.setTag("drawerRowGradientListCheck");
                    int value = AndroidUtilities.getIntDef("drawerRowGradient", 0);
                    textCell.setTextAndCheck(LocaleController.getString("RowGradientList", R.string.RowGradientList), value == 0 ? false : themePrefs.getBoolean("drawerRowGradientListCheck", false), true);
                }
            } else if (type == 5) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }
                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;

                if(i == headerGradientRow){
                    textCell.setTag("drawerHeaderGradient");
                    textCell.setMultilineDetail(false);
                    int value = themePrefs.getInt("drawerHeaderGradient", 0);
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientDisabled", R.string.RowGradientDisabled), false);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientTopBottom", R.string.RowGradientTopBottom), false);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientLeftRight", R.string.RowGradientLeftRight), false);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientTLBR", R.string.RowGradientTLBR), false);
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientBLTR", R.string.RowGradientBLTR), false);
                    }
                } else if(i == rowGradientRow){
                    textCell.setTag("drawerRowGradient");
                    textCell.setMultilineDetail(false);
                    int value = themePrefs.getInt("drawerRowGradient", 0);
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientDisabled", R.string.RowGradientDisabled), false);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientTopBottom", R.string.RowGradientTopBottom), false);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientLeftRight", R.string.RowGradientLeftRight), false);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientTLBR", R.string.RowGradientTLBR), false);
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("RowGradient", R.string.RowGradient), LocaleController.getString("RowGradientBLTR", R.string.RowGradientBLTR), false);
                    }
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
            else if ( i == avatarRadiusRow || i == avatarSizeRow || i == nameSizeRow ||  i == phoneSizeRow ||  i == optionSizeRow ||  i == versionSizeRow) {
                return 2;
            }
            else if ( i == headerColorRow || i == headerGradientColorRow || i == listColorRow ||  i == rowGradientColorRow || i == listDividerColorRow || i == iconColorRow || i == optionColorRow || i == versionColorRow  || i == avatarColorRow  || i == nameColorRow || i == phoneColorRow) {
                return 3;
            }
            else if (i == headerBackgroundCheckRow || i == hideBackgroundShadowRow || i == centerAvatarRow || i == rowGradientListCheckRow) {
                return 4;
            }
            else if (i == headerGradientRow || i == rowGradientRow) {
                return 5;
            }
            else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 6;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}

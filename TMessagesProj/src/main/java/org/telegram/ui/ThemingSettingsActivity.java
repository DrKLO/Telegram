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
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ColorSelectorDialog;
import org.telegram.ui.Components.NumberPicker;

import static org.telegram.ui.Components.ColorSelectorDialog.OnColorChangedListener;

public class ThemingSettingsActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private int sectionColorRow;
    private int titleColorRow;
    private int summaryColorRow;
    private int backgroundColorRow;
    private int dividerColorRow;
    private int shadowColorRow;

    private int headerSection2Row;
    private int headerColorRow;
    private int headerTitleColorRow;
    private int headerIconsColorRow;

    private int rowsSectionRow;
    private int rowsSection2Row;
    private int avatarColorRow;
    private int avatarRadiusRow;
    private int avatarSizeRow;
    private int headerStatusColorRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;

        headerSection2Row = rowCount++;
        headerColorRow  = rowCount++;
        headerTitleColorRow = rowCount++;
        headerStatusColorRow = rowCount++;
        headerIconsColorRow = rowCount++;
        avatarColorRow  = rowCount++;
        avatarRadiusRow  = rowCount++;
        avatarSizeRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        backgroundColorRow = rowCount++;
        shadowColorRow = rowCount++;
        sectionColorRow = rowCount++;
        titleColorRow = rowCount++;
        summaryColorRow = rowCount++;
        dividerColorRow = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

    }

    @Override
    public View createView(Context context) {
        if (fragmentView == null) {

            //actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);


            if (AndroidUtilities.isTablet()) {
                actionBar.setOccupyStatusBar(false);
            }
            actionBar.setTitle(LocaleController.getString("SettingsScreen", R.string.SettingsScreen));

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
                                commitInt("prefHeaderColor", color);
                            }
                        },themePrefs.getInt("prefHeaderColor", defColor), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == headerTitleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefHeaderTitleColor", color);
                            }
                        },themePrefs.getInt("prefHeaderTitleColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == headerStatusColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefHeaderStatusColor", color);
                            }
                        },themePrefs.getInt("prefHeaderStatusColor", AndroidUtilities.getIntDarkerColor("themeColor", -0x40)), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == headerIconsColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( "prefHeaderIconsColor", color);
                            }
                        },themePrefs.getInt( "prefHeaderIconsColor", 0xffffffff), CENTER, 0, true);
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
                                commitInt( "prefAvatarColor", color);
                            }
                        },themePrefs.getInt( "prefAvatarColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15)), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == avatarRadiusRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarRadius", R.string.AvatarRadius));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("prefAvatarRadius", 32);
                        numberPicker.setMinValue(1);
                        numberPicker.setMaxValue(32);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("prefAvatarRadius", numberPicker.getValue());
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
                        final int currentValue = themePrefs.getInt("prefAvatarSize", 42);
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(48);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("prefAvatarSize", numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
                    }
                    else if (i == backgroundColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( "prefBGColor", color);
                            }
                        },themePrefs.getInt( "prefBGColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == shadowColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( "prefShadowColor", color);
                            }
                        },themePrefs.getInt( "prefShadowColor", 0xfff0f0f0), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == sectionColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefSectionColor", color);
                            }
                        },themePrefs.getInt("prefSectionColor", defColor), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == titleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefTitleColor", color);
                            }
                        },themePrefs.getInt("prefTitleColor", 0xff212121), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == summaryColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefSummaryColor", color);
                            }
                        },themePrefs.getInt("prefSummaryColor", 0xff8a8a8a), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == dividerColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("prefDividerColor", color);
                            }
                        },themePrefs.getInt("prefDividerColor", 0xffd9d9d9), CENTER, 0, false);
                        colorDialog.show();
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
                        resetInt("prefHeaderColor");
                    } else if (i == headerTitleColorRow) {
                        resetInt("prefHeaderTitleColor");
                    } else if (i == headerStatusColorRow) {
                        resetInt("prefHeaderStatusColor");
                    } else if (i == headerIconsColorRow) {
                        resetInt("prefHeaderIconsColor");
                    } else if (i == avatarColorRow) {
                        resetInt("prefAvatarColor");
                    } else if (i == avatarRadiusRow) {
                        resetInt("prefAvatarRadius");
                    } else if (i == avatarSizeRow) {
                        resetInt("prefAvatarSize");
                    } else if (i == backgroundColorRow) {
                        resetInt("prefBGColor");
                    } else if (i == shadowColorRow) {
                        resetInt("prefShadowColor");
                    } else if (i == sectionColorRow) {
                        resetInt("prefSectionColor");
                    } else if (i == titleColorRow) {
                        resetInt("prefTitleColor");
                    } else if (i == summaryColorRow) {
                        resetInt("prefSummaryColor");
                    } else if (i == dividerColorRow) {
                        resetInt("prefDividerColor");
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
        updateTheme();
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
            return  i == headerColorRow || i == headerTitleColorRow || i == headerStatusColorRow  || i == headerIconsColorRow || i == avatarColorRow || i == avatarRadiusRow || i == avatarSizeRow || i == backgroundColorRow || i == shadowColorRow || i == sectionColorRow || i == titleColorRow || i == summaryColorRow || i == dividerColorRow;
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
            else if (type == 2){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;
                int defColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("prefHeaderColor", defColor), true);
                } else if (i == headerTitleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderTitleColor", R.string.HeaderTitleColor), themePrefs.getInt("prefHeaderTitleColor", 0xffffffff), true);
                } else if (i == headerStatusColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("StatusColor", R.string.StatusColor), themePrefs.getInt("prefHeaderStatusColor", AndroidUtilities.getIntDarkerColor("themeColor", -0x40)), true);
                } else if (i == headerIconsColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderIconsColor", R.string.HeaderIconsColor), themePrefs.getInt("prefHeaderIconsColor", 0xffffffff), true);
                } else if (i == avatarColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("AvatarColor", R.string.AvatarColor), themePrefs.getInt("prefAvatarColor", AndroidUtilities.getIntDarkerColor("themeColor", 0x15)), true);
                } else if (i == backgroundColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("BackgroundColor", R.string.BackgroundColor), themePrefs.getInt("prefBGColor", 0xffffffff), true);
                } else if (i == shadowColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ShadowColor", R.string.ShadowColor), themePrefs.getInt("prefShadowColor", 0xfff0f0f0), true);
                } else if (i == sectionColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("SectionColor", R.string.SectionColor), themePrefs.getInt("prefSectionColor", defColor), true);
                } else if (i == titleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("TitleColor", R.string.TitleColor), themePrefs.getInt("prefTitleColor", 0xff212121), true);
                } else if (i == summaryColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("SummaryColor", R.string.SummaryColor), themePrefs.getInt("prefSummaryColor", 0xff8a8a8a), true);
                } else if (i == dividerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("DividerColor", R.string.DividerColor), themePrefs.getInt("prefDividerColor", 0xffd9d9d9), true);
                }
            } else if (type == 3) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == avatarRadiusRow) {
                    int size = themePrefs.getInt("prefAvatarRadius", AndroidUtilities.isTablet() ? 35 : 32);
                    textCell.setTextAndValue(LocaleController.getString("AvatarRadius", R.string.AvatarRadius), String.format("%d", size), true);
                } else if (i == avatarSizeRow) {
                    int size = themePrefs.getInt("prefAvatarSize", AndroidUtilities.isTablet() ? 45 : 42);
                    textCell.setTextAndValue(LocaleController.getString("AvatarSize", R.string.AvatarSize), String.format("%d", size), false);
                }

            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if ( i == avatarRadiusRow || i == avatarSizeRow) {
                return 3;
            }
            if ( i == headerColorRow || i == headerTitleColorRow || i == headerStatusColorRow || i == headerIconsColorRow || i == avatarColorRow || i == backgroundColorRow || i == shadowColorRow || i == sectionColorRow || i == titleColorRow || i == summaryColorRow || i == dividerColorRow) {
                return 2;
            }
            else if ( i == headerSection2Row || i == rowsSection2Row ) {
                return 1;
            }
            else {
                return 0;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}

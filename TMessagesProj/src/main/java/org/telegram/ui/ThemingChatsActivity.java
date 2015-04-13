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
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorSelectorDialog;
import org.telegram.ui.Components.NumberPicker;

import java.util.ArrayList;
import java.util.List;

import static org.telegram.ui.Components.ColorSelectorDialog.OnColorChangedListener;

public class ThemingChatsActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private int headerSection2Row;
    private int headerColorRow;
    private int headerTitleColorRow;
    private int headerTitleRow;
    private int headerIconsColorRow;

    private int rowsSectionRow;
    private int rowsSection2Row;
    private int rowColorRow;
    private int dividerColorRow;
    private int nameSizeRow;
    private int nameColorRow;
    private int checksColorRow;
    private int muteColorRow;
    private int avatarRadiusRow;
    private int messageColorRow;
    private int memberColorRow;
    private int typingColorRow;
    private int messageSizeRow;
    private int timeColorRow;
    private int timeSizeRow;
    private int countColorRow;
    private int countSizeRow;
    private int countBGColorRow;
    private int floatingPencilColorRow;
    private int floatingBGColorRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerColorRow = rowCount++;
        headerTitleColorRow = rowCount++;
        headerTitleRow = rowCount++;
        headerIconsColorRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        rowColorRow = rowCount++;
        dividerColorRow = rowCount++;

        avatarRadiusRow  = rowCount++;
        nameColorRow = rowCount++;
        nameSizeRow = rowCount++;
        muteColorRow = rowCount++;
        checksColorRow = rowCount++;

        messageColorRow = rowCount++;
        messageSizeRow = rowCount++;
        memberColorRow = rowCount++;
        typingColorRow = rowCount++;
        timeColorRow = rowCount++;
        timeSizeRow = rowCount++;
        countColorRow = rowCount++;
        countSizeRow = rowCount++;
        countBGColorRow = rowCount++;

        floatingPencilColorRow = rowCount++;
        floatingBGColorRow = rowCount++;

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
            actionBar.setTitle(LocaleController.getString("MainScreen", R.string.MainScreen));

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
                    int defColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
                    int darkColor = AndroidUtilities.getIntDarkerColor("themeColor", 0x15);
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
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, defColor), CENTER, 0, false);
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
                                commitInt( key, color);
                            }
                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, false);
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
                                commitInt( key, color);
                            }
                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == rowColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, false);
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
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xffdcdcdc), CENTER, 0, false);
                        colorDialog.show();
                    } /*else if (i == usernameTitleRow) {
                        boolean b = themePrefs.getBoolean( key, true);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean( key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }
                    }*/ else if (i == headerTitleRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("HeaderTitle", R.string.HeaderTitle));
                        int user_id = UserConfig.getClientUserId();
                        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        List<CharSequence> array = new ArrayList<>();
                        array.add( LocaleController.getString("AppName", R.string.AppName));
                        array.add( LocaleController.getString("ShortAppName", R.string.ShortAppName) );
                        String usr = "";
                        if (user != null && (user.first_name != null || user.last_name != null)) {
                            usr = ContactsController.formatName(user.first_name, user.last_name);
                            array.add(usr);
                        }
                        if (user != null && user.username != null && user.username.length() != 0) {
                            usr = "@" + user.username;
                            array.add(usr);
                        }
                        array.add("");
                        String[] simpleArray = new String[ array.size() ];
                        array.toArray( new String[ array.size() ]);
                        builder.setItems(array.toArray(simpleArray), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                                themePrefs.edit().putInt("chatsHeaderTitle", which).commit();
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == nameColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xff000000), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == muteColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xffa8a8a8), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == checksColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, defColor), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == messageColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xff8f8f8f), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == memberColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, darkColor), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == typingColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, defColor), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == timeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xff999999), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == countColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == countBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, defColor), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == avatarRadiusRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarRadius", R.string.AvatarRadius));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 32);
                        numberPicker.setMinValue(1);
                        numberPicker.setMaxValue(32);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);

                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        });

                        showAlertDialog(builder);
                    } else if (i == nameSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("NameSize", R.string.NameSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 17);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        AlertDialog dialog = builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        }).create();

                        //dialog.show();
                        //Button btn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                        //btn.setTextColor(0xff0000ff);
                        showAlertDialog(builder);

                    } else if (i == messageSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("MessageSize", R.string.MessageSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 16);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == timeSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TimeDateSize", R.string.TimeDateSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 13);
                        numberPicker.setMinValue(5);
                        numberPicker.setMaxValue(25);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == countSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("CountSize", R.string.CountSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 13);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == floatingPencilColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == floatingBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( key, color);
                            }

                        },themePrefs.getInt( key, defColor), CENTER, 0, true);
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
                    resetPref(view.getTag().toString());
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
        if(key != null)editor.remove(key);
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
            return  i == headerColorRow || i == headerTitleColorRow || i == headerIconsColorRow || i == headerTitleRow ||
                    i == rowColorRow || i == dividerColorRow || i == avatarRadiusRow ||
                    i == nameColorRow || i == muteColorRow || i == checksColorRow || i == nameSizeRow || i == messageColorRow || i == memberColorRow || i == typingColorRow || i == messageSizeRow ||
                    i == timeColorRow || i == timeSizeRow || i == countColorRow || i == countSizeRow || i == countBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow;
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
            } else if (type == 1) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == headerSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("Header", R.string.Header));
                } else if (i == rowsSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("ChatsList", R.string.ChatsList));
                }
            } else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == avatarRadiusRow) {
                    textCell.setTag("chatsAvatarRadius");
                    int size = themePrefs.getInt("chatsAvatarRadius", AndroidUtilities.isTablet() ? 35 : 32);
                    textCell.setTextAndValue(LocaleController.getString("AvatarRadius", R.string.AvatarRadius), String.format("%d", size), true);
                } else if (i == nameSizeRow) {
                    textCell.setTag("chatsNameSize");
                    int size = themePrefs.getInt("chatsNameSize", AndroidUtilities.isTablet() ? 19 : 17);
                    textCell.setTextAndValue(LocaleController.getString("NameSize", R.string.NameSize), String.format("%d", size), true);
                } else if (i == messageSizeRow) {
                    textCell.setTag("chatsMessageSize");
                    int size = themePrefs.getInt("chatsMessageSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("MessageSize", R.string.MessageSize), String.format("%d", size), true);
                } else if (i == timeSizeRow) {
                    textCell.setTag("chatsTimeSize");
                    int size = themePrefs.getInt("chatsTimeSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("TimeDateSize", R.string.TimeDateSize), String.format("%d", size), true);
                } else if (i == countSizeRow) {
                    textCell.setTag("chatsCountSize");
                    int size = themePrefs.getInt("chatsCountSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("CountSize", R.string.CountSize), String.format("%d", size), true);
                }

            } else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                if (i == headerColorRow) {
                    textCell.setTag("chatsHeaderColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("chatsHeaderColor", defColor), true);
                } else if (i == headerTitleColorRow) {
                    textCell.setTag("chatsHeaderTitleColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTitleColor", R.string.HeaderTitleColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == headerIconsColorRow) {
                    textCell.setTag("chatsHeaderIconsColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderIconsColor", R.string.HeaderIconsColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == rowColorRow) {
                    textCell.setTag("chatsRowColor");
                    textCell.setTextAndColor(LocaleController.getString("RowColor", R.string.RowColor), themePrefs.getInt("chatsRowColor", 0xffffffff), true);
                } else if (i == dividerColorRow) {
                    textCell.setTag("chatsDividerColor");
                    textCell.setTextAndColor(LocaleController.getString("DividerColor", R.string.DividerColor), themePrefs.getInt("chatsDividerColor", 0xffdcdcdc), true);
                } else if (i == nameColorRow) {
                    textCell.setTag("chatsNameColor");
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("chatsNameColor", 0xff000000), true);
                } else if (i == muteColorRow) {
                    textCell.setTag("chatsMuteColor");
                    textCell.setTextAndColor(LocaleController.getString("MuteColor", R.string.MuteColor), themePrefs.getInt("chatsMuteColor", 0xffa8a8a8), true);
                } else if (i == checksColorRow) {
                    textCell.setTag("chatsChecksColor");
                    textCell.setTextAndColor(LocaleController.getString("ChecksColor", R.string.ChecksColor), themePrefs.getInt("chatsChecksColor", defColor), true);
                } else if (i == messageColorRow) {
                    textCell.setTag("chatsMessageColor");
                    textCell.setTextAndColor(LocaleController.getString("MessageColor", R.string.MessageColor), themePrefs.getInt("chatsMessageColor", 0xff8f8f8f), true);
                } else if (i == memberColorRow) {
                    textCell.setTag("chatsMemberColor");
                    textCell.setTextAndColor(LocaleController.getString("MemberColor", R.string.MemberColor), themePrefs.getInt("chatsMemberColor", darkColor), true);
                } else if (i == typingColorRow) {
                    textCell.setTag("chatsTypingColor");
                    textCell.setTextAndColor(LocaleController.getString("TypingColor", R.string.TypingColor), themePrefs.getInt(textCell.getTag().toString(), defColor), true);
                } else if (i == timeColorRow) {
                    textCell.setTag("chatsTimeColor");
                    textCell.setTextAndColor(LocaleController.getString("TimeDateColor", R.string.TimeDateColor), themePrefs.getInt("chatsTimeColor", 0xff999999), true);
                } else if (i == countColorRow) {
                    textCell.setTag("chatsCountColor");
                    textCell.setTextAndColor(LocaleController.getString("CountColor", R.string.CountColor), themePrefs.getInt("chatsCountColor", 0xffffffff), true);
                } else if (i == countBGColorRow) {
                    textCell.setTag("chatsCountBGColor");
                    textCell.setTextAndColor(LocaleController.getString("CountBGColor", R.string.CountBGColor), themePrefs.getInt("chatsCountBGColor", defColor), true);
                } else if (i == floatingPencilColorRow) {
                    textCell.setTag("chatsFloatingPencilColor");
                    textCell.setTextAndColor(LocaleController.getString("FloatingPencilColor", R.string.FloatingPencilColor), themePrefs.getInt("chatsFloatingPencilColor", 0xffffffff), true);
                } else if (i == floatingBGColorRow) {
                    textCell.setTag("chatsFloatingBGColor");
                    textCell.setTextAndColor(LocaleController.getString("FloatingBGColor", R.string.FloatingBGColor), themePrefs.getInt("chatsFloatingBGColor", defColor), true);
                }
            } /*else if (type == 4) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                }
                TextCheckCell textCell = (TextCheckCell) view;

                if (i == usernameTitleRow) {
                    textCell.setTag("chatsUsernameTitle");
                    textCell.setTextAndCheck(LocaleController.getString("UsernameTitle", R.string.UsernameTitle), themePrefs.getBoolean("chatsUsernameTitle", false), false);
                }
            }*/ else if (type == 5) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }

                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                if (i == headerTitleRow) {
                    textCell.setTag("chatsHeaderTitle");
                    textCell.setMultilineDetail(false);
                    int value = 0;
                    value = themePrefs.getInt("chatsHeaderTitle", 0);
                    Log.e("chatsHeaderTitle", "" + value);
                    int user_id = UserConfig.getClientUserId();
                    TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                    String text;
                    if (user != null && user.username != null && user.username.length() != 0) {
                        text = "@" + user.username;
                    } else {
                        text = "-";
                    }
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), LocaleController.getString("AppName", R.string.AppName), false);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), LocaleController.getString("ShortAppName", R.string.ShortAppName), false);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), ContactsController.formatName(user.first_name, user.last_name), false);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), text, false);
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), "", false);
                    }
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if ( i == rowsSectionRow ) {
                return 0;
            } else if ( i == headerSection2Row || i == rowsSection2Row ) {
                return 1;
            } else if ( i == avatarRadiusRow || i == nameSizeRow ||  i == messageSizeRow || i == timeSizeRow || i == countSizeRow ) {
                return 2;
            } else if ( i == headerColorRow || i == headerTitleColorRow || i == headerIconsColorRow  ||
                        i == rowColorRow || i == dividerColorRow || i == nameColorRow || i == muteColorRow || i == checksColorRow || i == messageColorRow  || i == memberColorRow || i == typingColorRow || i == timeColorRow || i == countColorRow ||
                        i == countBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow) {
                return 3;
            }/* else if (i == usernameTitleRow) {
                return 4;
            }*/ else if (i == headerTitleRow) {
                return 5;
            } else {
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

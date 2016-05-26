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
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
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
    private int countSilentBGColorRow;
    private int floatingPencilColorRow;
    private int floatingBGColorRow;
    private int avatarSizeRow;
    private int avatarMarginLeftRow;
    private int unknownNameColorRow;
    private int groupNameColorRow;
    private int groupNameSizeRow;
    private int mediaColorRow;
    private int groupIconColorRow;
    private int rowGradientRow;
    private int rowGradientColorRow;
    private int rowGradientListCheckRow;
    private int headerGradientRow;
    private int headerGradientColorRow;
    private int highlightSearchColorRow;

    private int hideStatusIndicatorCheckRow;
    private int headerTabIconColorRow;
    private int headerTabUnselectedIconColorRow;
    private int headerTabCounterColorRow;
    private int headerTabCounterBGColorRow;
    private int headerTabCounterSilentBGColorRow;
    private int headerTabCounterSizeRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerColorRow = rowCount++;
        headerGradientRow = rowCount++;
        headerGradientColorRow = rowCount++;
        headerTitleColorRow = rowCount++;
        headerTitleRow = rowCount++;
        headerIconsColorRow = rowCount++;
        headerTabIconColorRow = rowCount++;
        headerTabUnselectedIconColorRow = rowCount++;
        headerTabCounterColorRow = rowCount++;
        headerTabCounterBGColorRow = rowCount++;
        headerTabCounterSilentBGColorRow = rowCount++;
        headerTabCounterSizeRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        rowColorRow = rowCount++;
        rowGradientRow = rowCount++;
        rowGradientColorRow = rowCount++;
        //rowGradientListCheckRow = rowCount++;
        dividerColorRow = rowCount++;

        avatarRadiusRow = rowCount++;
        avatarSizeRow = rowCount++;
        avatarMarginLeftRow = rowCount++;
        hideStatusIndicatorCheckRow = rowCount++;

        nameColorRow = rowCount++;
        unknownNameColorRow = rowCount++;
        nameSizeRow = rowCount++;
        groupNameColorRow = rowCount++;
        groupNameSizeRow = rowCount++;
        groupIconColorRow = rowCount++;
        muteColorRow = rowCount++;
        checksColorRow = rowCount++;

        messageColorRow = rowCount++;
        messageSizeRow = rowCount++;
        memberColorRow = rowCount++;
        mediaColorRow = rowCount++;
        typingColorRow = rowCount++;
        timeColorRow = rowCount++;
        timeSizeRow = rowCount++;
        countColorRow = rowCount++;
        countSizeRow = rowCount++;
        countBGColorRow = rowCount++;
        //countSilentColorRow = rowCount++;
        countSilentBGColorRow = rowCount++;

        floatingPencilColorRow = rowCount++;
        floatingBGColorRow = rowCount++;

        highlightSearchColorRow = rowCount++;

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
                    } else if (i == headerGradientColorRow) {
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
                    } else if (i == headerTitleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt(key, 0xffffffff), CENTER, 0, true);
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
                                commitInt(key, color);
                            }
                        },themePrefs.getInt(key, themePrefs.getInt("chatsHeaderIconsColor", 0xffffffff)), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerTabIconColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerTabUnselectedIconColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt( key, AndroidUtilities.getIntAlphaColor("chatsHeaderTabIconColor", defColor, 0.3f)), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerTabCounterColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt(key, 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerTabCounterBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt(key, 0xffd32f2f), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == headerTabCounterSilentBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(key, color);
                            }
                        },themePrefs.getInt(key, 0xffb9b9b9), CENTER, 0, true);
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
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            }

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, false);
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
                                commitInt(key, color);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            }
                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == rowGradientListCheckRow) {
                        boolean b = themePrefs.getBoolean( key, false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean(key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }

                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
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

                        },themePrefs.getInt( key, 0xffdcdcdc), CENTER, 0, true);
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
                        showDialog(builder.create());
                    } else if (i == headerGradientRow) {
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
                                themePrefs.edit().putInt("chatsHeaderGradient", which).commit();
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
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
                                themePrefs.edit().putInt("chatsRowGradient", which).commit();
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                if (listView != null) {
                                    listView.invalidateViews();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
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

                        },themePrefs.getInt( key, 0xff212121), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == groupNameColorRow) {
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

                        },themePrefs.getInt( key, themePrefs.getInt("chatsNameColor", 0xff212121)), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == unknownNameColorRow) {
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

                        },themePrefs.getInt( key, themePrefs.getInt("chatsNameColor", 0xff212121)), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == groupIconColorRow) {
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

                        },themePrefs.getInt( key, themePrefs.getInt("chatsGroupNameColor", 0xff000000)), CENTER, 0, true);
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

                        },themePrefs.getInt( key, 0xff8f8f8f), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == highlightSearchColorRow) {
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

                        },themePrefs.getInt( key, AndroidUtilities.getIntDarkerColor("themeColor", -0x40)), CENTER, 0, true);
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

                        },themePrefs.getInt( key, darkColor), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == mediaColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt( "chatsMediaColor", color);
                            }

                        },themePrefs.getInt( "chatsMediaColor", themePrefs.getInt("chatsMemberColor", darkColor)), CENTER, 0, true);

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

                        },themePrefs.getInt( key, defColor), CENTER, 0, true);

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

                        },themePrefs.getInt( key, 0xff999999), CENTER, 0, true);
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

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, true);
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
                    } else if (i == countSilentBGColorRow) {
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

                        },themePrefs.getInt( key, themePrefs.getInt("chatsCountBGColor", 0xffb9b9b9)), CENTER, 0, true);
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

                        showDialog(builder.create());
                    } else if (i == headerTabCounterSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("CountSize", R.string.CountSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 11);
                        numberPicker.setMinValue(9);
                        numberPicker.setMaxValue(14);
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
                        showDialog(builder.create());
                    } else if (i == avatarSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarSize", R.string.AvatarSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, 52);
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(72);
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

                        showDialog(builder.create());
                    } else if (i == avatarMarginLeftRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AvatarMarginLeft", R.string.AvatarMarginLeft));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, AndroidUtilities.isTablet() ? 13 : 9);
                        numberPicker.setMinValue(0);
                        numberPicker.setMaxValue(18);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);

                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt(key, numberPicker.getValue());
                                }
                            }
                        });

                        showDialog(builder.create());
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
                        showDialog(builder.create());

                    } else if (i == groupNameSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("GroupNameSize", R.string.GroupNameSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt( key, themePrefs.getInt("chatsNameSize", 17));
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt( key, numberPicker.getValue());
                                }
                            }
                        }).create();
                        showDialog(builder.create());

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
                        showDialog(builder.create());
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
                                    commitInt(key, numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
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
                                    commitInt(key, numberPicker.getValue());
                                }
                            }
                        });
                        showDialog(builder.create());
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
                    } else if (i == hideStatusIndicatorCheckRow) {
                        boolean b = themePrefs.getBoolean( key, false);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean( key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }
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
            int g = AndroidUtilities.getIntDef("chatsRowGradient",0);
            return  i == headerColorRow || i == headerGradientRow || (AndroidUtilities.getIntDef("chatsHeaderGradient", 0) != 0 && i == headerGradientColorRow) || i == headerTitleColorRow || i == headerIconsColorRow || i == headerTabIconColorRow || i == headerTabUnselectedIconColorRow || i == headerTitleRow || i == headerTabCounterColorRow || i == headerTabCounterBGColorRow || i == headerTabCounterSilentBGColorRow || i == headerTabCounterSizeRow ||
                    i == rowColorRow || i == rowGradientRow || (g != 0 &&  i == rowGradientColorRow) || (g != 0 && i == rowGradientListCheckRow) || i == dividerColorRow || i == avatarRadiusRow ||  i == avatarSizeRow ||   i == avatarMarginLeftRow || i == hideStatusIndicatorCheckRow ||
                    i == nameColorRow || i == groupNameColorRow || i == unknownNameColorRow || i == groupIconColorRow || i == muteColorRow || i == checksColorRow || i == nameSizeRow || i == groupNameSizeRow || i == messageColorRow || i == highlightSearchColorRow || i == memberColorRow || i == mediaColorRow || i == typingColorRow || i == messageSizeRow ||
                    i == timeColorRow || i == timeSizeRow || i == countColorRow || i == countSizeRow || i == countBGColorRow /*|| i == countSilentColorRow*/ || i == countSilentBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow;
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
                } else if (i == headerTabCounterSizeRow) {
                    textCell.setTag("chatsHeaderTabCounterSize");
                    int size = themePrefs.getInt("chatsHeaderTabCounterSize", AndroidUtilities.isTablet() ? 13 : 11);
                    textCell.setTextAndValue(LocaleController.getString("CountSize", R.string.CountSize), String.format("%d", size), true);
                } else if (i == avatarSizeRow) {
                    textCell.setTag("chatsAvatarSize");
                    int size = themePrefs.getInt("chatsAvatarSize", AndroidUtilities.isTablet() ? 55 : 52);
                    textCell.setTextAndValue(LocaleController.getString("AvatarSize", R.string.AvatarSize), String.format("%d", size), true);
                } else if (i == avatarMarginLeftRow) {
                    textCell.setTag("chatsAvatarMarginLeft");
                    int size = themePrefs.getInt("chatsAvatarMarginLeft", AndroidUtilities.isTablet() ? 13 : 9);
                    textCell.setTextAndValue(LocaleController.getString("AvatarMarginLeft", R.string.AvatarMarginLeft), String.format("%d", size), true);
                } else if (i == nameSizeRow) {
                    textCell.setTag("chatsNameSize");
                    int size = themePrefs.getInt("chatsNameSize", AndroidUtilities.isTablet() ? 19 : 17);
                    textCell.setTextAndValue(LocaleController.getString("NameSize", R.string.NameSize), String.format("%d", size), true);
                } else if (i == groupNameSizeRow) {
                    textCell.setTag("chatsGroupNameSize");
                    int size = themePrefs.getInt("chatsGroupNameSize", themePrefs.getInt("chatsNameSize", AndroidUtilities.isTablet() ? 19 : 17));
                    textCell.setTextAndValue(LocaleController.getString("GroupNameSize", R.string.GroupNameSize), String.format("%d", size), true);
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
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("chatsHeaderColor", defColor), false);
                } else if (i == headerGradientColorRow) {
                    textCell.setTag("chatsHeaderGradientColor");
                    textCell.setTextAndColor(LocaleController.getString("RowGradientColor", R.string.RowGradientColor), themePrefs.getInt("chatsHeaderGradient", 0) == 0 ? 0x00000000 : themePrefs.getInt("chatsHeaderGradientColor", defColor), true);
                } else if (i == headerTitleColorRow) {
                    textCell.setTag("chatsHeaderTitleColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTitleColor", R.string.HeaderTitleColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == headerIconsColorRow) {
                    textCell.setTag("chatsHeaderIconsColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderIconsColor", R.string.HeaderIconsColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == headerTabIconColorRow) {
                    textCell.setTag("chatsHeaderTabIconColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTabIconColor", R.string.HeaderTabIconColor), themePrefs.getInt(textCell.getTag().toString(), themePrefs.getInt("chatsHeaderIconsColor", 0xffffffff)), true);
                } else if (i == headerTabUnselectedIconColorRow) {
                    textCell.setTag("chatsHeaderTabUnselectedIconColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTabUnselectedIconColor", R.string.HeaderTabUnselectedIconColor), themePrefs.getInt(textCell.getTag().toString(), themePrefs.getInt("chatsHeaderTabUnselectedIconColor", AndroidUtilities.getIntAlphaColor("chatsHeaderTabIconColor", defColor, 0.3f))), true);
                } else if (i == headerTabCounterColorRow) {
                    textCell.setTag("chatsHeaderTabCounterColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTabCounterColor", R.string.HeaderTabCounterColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == headerTabCounterBGColorRow) {
                    textCell.setTag("chatsHeaderTabCounterBGColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderTabCounterBGColor", R.string.HeaderTabCounterBGColor), themePrefs.getInt(textCell.getTag().toString(), 0xffD32F2F), false);
                } else if (i == headerTabCounterSilentBGColorRow) {
                    textCell.setTag("chatsHeaderTabCounterSilentBGColor");
                    textCell.setTextAndColor(LocaleController.getString("CountSilentBGColor", R.string.CountSilentBGColor), themePrefs.getInt(textCell.getTag().toString(), 0xffb9b9b9), false);
                } else if (i == rowColorRow) {
                    textCell.setTag("chatsRowColor");
                    textCell.setTextAndColor(LocaleController.getString("RowColor", R.string.RowColor), themePrefs.getInt("chatsRowColor", 0xffffffff), false);
                } else if (i == rowGradientColorRow) {
                    textCell.setTag("chatsRowGradientColor");
                    textCell.setTextAndColor(LocaleController.getString("RowGradientColor", R.string.RowGradientColor), themePrefs.getInt("chatsRowGradient", 0) == 0 ? 0x00000000 : themePrefs.getInt("chatsRowGradientColor", 0xffffffff), true);
                } else if (i == dividerColorRow) {
                    textCell.setTag("chatsDividerColor");
                    textCell.setTextAndColor(LocaleController.getString("DividerColor", R.string.DividerColor), themePrefs.getInt("chatsDividerColor", 0xffdcdcdc), true);
                } else if (i == nameColorRow) {
                    textCell.setTag("chatsNameColor");
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("chatsNameColor", 0xff212121), true);
                } else if (i == groupNameColorRow) {
                    textCell.setTag("chatsGroupNameColor");
                    textCell.setTextAndColor(LocaleController.getString("GroupNameColor", R.string.GroupNameColor), themePrefs.getInt("chatsGroupNameColor", themePrefs.getInt("chatsNameColor", 0xff212121)), true);
                } else if (i == unknownNameColorRow) {
                    textCell.setTag("chatsUnknownNameColor");
                    textCell.setTextAndColor(LocaleController.getString("UnknownNameColor", R.string.UnknownNameColor), themePrefs.getInt("chatsUnknownNameColor", themePrefs.getInt("chatsNameColor", 0xff212121)), true);
                } else if (i == groupIconColorRow) {
                    textCell.setTag("chatsGroupIconColor");
                    textCell.setTextAndColor(LocaleController.getString("GroupIconColor", R.string.GroupIconColor), themePrefs.getInt("chatsGroupIconColor", themePrefs.getInt("chatsGroupNameColor", 0xff000000)), true);
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
                } else if (i == mediaColorRow) {
                    textCell.setTag("chatsMediaColor");
                    textCell.setTextAndColor(LocaleController.getString("MediaColor", R.string.MediaColor), themePrefs.getInt("chatsMediaColor", themePrefs.getInt("chatsMemberColor", darkColor)), true);
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
                } /*else if (i == countSilentColorRow) {
                    textCell.setTag("chatsCountSilentColor");
                    textCell.setTextAndColor(LocaleController.getString("CountSilentColor", R.string.CountSilentColor), themePrefs.getInt("chatsCountSilentColor", themePrefs.getInt("chatsCountColor", 0xffffffff)), true);
                }*/ else if (i == countSilentBGColorRow) {
                    textCell.setTag("chatsCountSilentBGColor");
                    textCell.setTextAndColor(LocaleController.getString("CountSilentBGColor", R.string.CountSilentBGColor), themePrefs.getInt("chatsCountSilentBGColor", themePrefs.getInt("chatsCountBGColor", 0xffb9b9b9)), true);
                } else if (i == floatingPencilColorRow) {
                    textCell.setTag("chatsFloatingPencilColor");
                    textCell.setTextAndColor(LocaleController.getString("FloatingPencilColor", R.string.FloatingPencilColor), themePrefs.getInt("chatsFloatingPencilColor", 0xffffffff), true);
                } else if (i == floatingBGColorRow) {
                    textCell.setTag("chatsFloatingBGColor");
                    textCell.setTextAndColor(LocaleController.getString("FloatingBGColor", R.string.FloatingBGColor), themePrefs.getInt("chatsFloatingBGColor", defColor), true);
                } else if (i == highlightSearchColorRow) {
                    textCell.setTag("chatsHighlightSearchColor");
                    textCell.setTextAndColor(LocaleController.getString("HighlightSearchColor", R.string.HighlightSearchColor), themePrefs.getInt("chatsHighlightSearchColor", AndroidUtilities.getIntDarkerColor("themeColor", -0x40)), false);
                }
            } else if (type == 4) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                }
                TextCheckCell textCell = (TextCheckCell) view;
                if (i == rowGradientListCheckRow) {
                    textCell.setTag("chatsRowGradientListCheck");
                    int value = AndroidUtilities.getIntDef("chatsRowGradient", 0);
                    textCell.setTextAndCheck(LocaleController.getString("RowGradientList", R.string.RowGradientList), value == 0 ? false : themePrefs.getBoolean("chatsRowGradientListCheck", false), true);
                } else if (i == hideStatusIndicatorCheckRow) {
                    textCell.setTag("chatsHideStatusIndicator");
                    textCell.setTextAndCheck(LocaleController.getString("HideStatusIndicator", R.string.HideStatusIndicator), themePrefs.getBoolean("chatsHideStatusIndicator", false), true);
                }
            } else if (type == 5) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }

                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                if (i == headerTitleRow) {
                    textCell.setTag("chatsHeaderTitle");
                    textCell.setMultilineDetail(false);
                    int value = themePrefs.getInt("chatsHeaderTitle", 0);
                    int user_id = UserConfig.getClientUserId();
                    TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                    String text;
                    if (user != null && user.username != null && user.username.length() != 0) {
                        text = "@" + user.username;
                    } else {
                        text = "-";
                    }
                    if (value == 0) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), LocaleController.getString("AppName", R.string.AppName), true);
                    } else if (value == 1) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), LocaleController.getString("ShortAppName", R.string.ShortAppName), true);
                    } else if (value == 2) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), ContactsController.formatName(user.first_name, user.last_name), true);
                    } else if (value == 3) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), text, true);
                    } else if (value == 4) {
                        textCell.setTextAndValue(LocaleController.getString("HeaderTitle", R.string.HeaderTitle), "", true);
                    }
                } else if(i == headerGradientRow){
                    textCell.setTag("chatsHeaderGradient");
                    textCell.setMultilineDetail(false);
                    int value = themePrefs.getInt("chatsHeaderGradient", 0);
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
                    textCell.setTag("chatsRowGradient");
                    textCell.setMultilineDetail(false);
                    int value = themePrefs.getInt("chatsRowGradient", 0);
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
            } else if ( i == headerSection2Row || i == rowsSection2Row ) {
                return 1;
            } else if ( i == avatarRadiusRow || i == avatarSizeRow || i == avatarMarginLeftRow || i == nameSizeRow || i == groupNameSizeRow ||  i == messageSizeRow || i == timeSizeRow || i == countSizeRow || i == headerTabCounterSizeRow) {
                return 2;
            } else if ( i == headerColorRow || i == headerGradientColorRow  || i == headerTitleColorRow || i == headerIconsColorRow  || i == headerTabIconColorRow || i == headerTabUnselectedIconColorRow || i == headerTabCounterColorRow  || i == headerTabCounterBGColorRow  || i == headerTabCounterSilentBGColorRow  ||
                        i == rowColorRow || i == rowGradientColorRow || i == dividerColorRow || i == nameColorRow || i == groupNameColorRow || i == unknownNameColorRow || i == groupIconColorRow || i == muteColorRow || i == checksColorRow || i == messageColorRow || i == highlightSearchColorRow || i == memberColorRow || i == mediaColorRow || i == typingColorRow || i == timeColorRow || i == countColorRow ||
                        i == countBGColorRow /*|| i == countSilentColorRow*/ || i == countSilentBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow) {
                return 3;
            } else if (i == rowGradientListCheckRow || i == hideStatusIndicatorCheckRow) {
                return 4;
            } else if (i == headerTitleRow || i == headerGradientRow || i == rowGradientRow) {
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

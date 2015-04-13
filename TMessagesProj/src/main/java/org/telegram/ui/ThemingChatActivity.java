/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
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
import org.telegram.android.MessagesController;
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

public class ThemingChatActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private int headerSection2Row;
    private int muteColorRow;
    private int headerColorRow;
    private int headerIconsColorRow;

    private int rowsSectionRow;
    private int rowsSection2Row;
    private int rBubbleColorRow;
    private int lBubbleColorRow;
    private int rTextColorRow;
    private int rLinkColorRow;
    private int textSizeRow;
    private int lTextColorRow;
    private int lLinkColorRow;
    private int rTimeColorRow;
    private int lTimeColorRow;
    private int checksColorRow;
    private int dateBubbleColorRow;
    private int nameColorRow;
    private int nameSizeRow;
    private int statusColorRow;
    private int statusSizeRow;
    private int dateColorRow;
    private int dateSizeRow;
    private int timeSizeRow;
    private int editTextColorRow;
    private int editTextSizeRow;
    private int editTextBGColorRow;
    private int editTextIconsColorRow;
    private int emojiViewBGColorRow;
    private int emojiViewTabColorRow;
    private int sendColorRow;
    private int memberColorCheckRow;
    private int memberColorRow;
    private int forwardNameColorRow;
    private int avatarRadiusRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerColorRow = rowCount++;
        headerIconsColorRow = rowCount++;
        //muteColorRow = rowCount++;

        nameSizeRow = rowCount++;
        nameColorRow = rowCount++;
        statusSizeRow = rowCount++;
        statusColorRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;

        avatarRadiusRow  = rowCount++;
        textSizeRow = rowCount++;
        rTextColorRow = rowCount++;
        rLinkColorRow = rowCount++;
        lTextColorRow = rowCount++;
        lLinkColorRow = rowCount++;

        timeSizeRow = rowCount++;
        rTimeColorRow = rowCount++;
        lTimeColorRow = rowCount++;
        checksColorRow = rowCount++;

        dateSizeRow = rowCount++;
        dateColorRow = rowCount++;

        rBubbleColorRow = rowCount++;
        lBubbleColorRow = rowCount++;
        dateBubbleColorRow = rowCount++;

        memberColorCheckRow = rowCount++;
        memberColorRow = rowCount++;
        forwardNameColorRow = rowCount++;

        sendColorRow = rowCount++;
        editTextSizeRow = rowCount++;
        editTextColorRow = rowCount++;
        editTextBGColorRow = rowCount++;
        editTextIconsColorRow = rowCount++;

        emojiViewBGColorRow = rowCount++;
        emojiViewTabColorRow = rowCount++;

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
            actionBar.setTitle(LocaleController.getString("ChatScreen", R.string.ChatScreen));

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
                                commitInt("chatHeaderColor", color);
                            }

                        },themePrefs.getInt("chatHeaderColor", defColor), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == memberColorCheckRow) {
                        boolean b = themePrefs.getBoolean( key, true);
                        SharedPreferences.Editor editor = themePrefs.edit();
                        editor.putBoolean( key, !b);
                        editor.commit();
                        if (view instanceof TextCheckCell) {
                            ((TextCheckCell) view).setChecked(!b);
                        }
                        if (listView != null) {
                            listView.invalidateViews();
                        }
                    } else if (i == memberColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatMemberColor", color);
                            }

                        },themePrefs.getInt("chatMemberColor", darkColor), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == forwardNameColorRow) {
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

                        },themePrefs.getInt(key, darkColor), CENTER, 0, true);
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

                        },themePrefs.getInt( key, 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == rBubbleColorRow) {
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

                        },themePrefs.getInt(key, AndroidUtilities.getDefBubbleColor()), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == lBubbleColorRow) {
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
                    } else if (i == rTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRTextColor", color);
                            }

                        },themePrefs.getInt("chatRTextColor", 0xff000000), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == lTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLTextColor", color);
                            }

                        },themePrefs.getInt("chatLTextColor", 0xff000000), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == rLinkColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRLinkColor", color);
                            }

                        },themePrefs.getInt("chatRLinkColor", defColor), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == lLinkColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLLinkColor", color);
                            }

                        },themePrefs.getInt("chatLLinkColor", defColor), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == rTimeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRTimeColor", color);
                            }

                        },themePrefs.getInt("chatRTimeColor", darkColor), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == lTimeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLTimeColor", color);
                            }

                        },themePrefs.getInt("chatLTimeColor", 0xffa1aab3), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == dateBubbleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatDateBubbleColor", color);
                            }

                        },themePrefs.getInt("chatDateBubbleColor", 0x59000000), CENTER, 0, true);

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
                    } else if (i == nameColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatNameColor", color);
                            }

                        },themePrefs.getInt("chatNameColor", 0xffffffff), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == sendColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatSendIconColor", color);
                            }

                        },themePrefs.getInt("chatSendIconColor", AndroidUtilities.getIntColor("chatEditTextIconsColor")), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == editTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEditTextColor", color);
                            }

                        },themePrefs.getInt("chatEditTextColor", 0xff000000), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == editTextBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEditTextBGColor", color);
                            }

                        },themePrefs.getInt("chatEditTextBGColor", 0xffffffff), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == editTextIconsColorRow) {
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

                        },themePrefs.getInt( key, 0xffadadad), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == emojiViewBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEmojiViewBGColor", color);
                            }

                        },themePrefs.getInt("chatEmojiViewBGColor", 0xff222222), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == emojiViewTabColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEmojiViewTabColor", color);
                            }

                        },themePrefs.getInt("chatEmojiViewTabColor", darkColor), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == statusColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatStatusColor", color);
                            }

                        },themePrefs.getInt("chatStatusColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), CENTER, 0, false);

                        colorDialog.show();
                    }  else if (i == dateColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatDateColor", color);
                            }

                        },themePrefs.getInt("chatDateColor", 0xffffffff), CENTER, 0, false);
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
                                commitInt("chatChecksColor", color);
                            }

                        },themePrefs.getInt("chatChecksColor", defColor), CENTER, 0, true);
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
                        final int currentValue = themePrefs.getInt("chatNameSize", 18);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatNameSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == statusSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("StatusSize", R.string.StatusSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatStatusSize", 14);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(22);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("chatStatusSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == textSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TextSize", R.string.TextSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatTextSize", 16);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatTextSize", numberPicker.getValue());
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putInt("fons_size", numberPicker.getValue());
                                    MessagesController.getInstance().fontSize = numberPicker.getValue();
                                    editor.commit();
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == timeSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TimeSize", R.string.TimeSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatTimeSize", 12);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatTimeSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == dateSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("DateSize", R.string.DateSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatDateSize", 16);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatDateSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    }  else if (i == editTextSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("EditTextSize", R.string.EditTextSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatEditTextSize", 18);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(28);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatEditTextSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    //if(view.getTag() != null)resetPref(view.getTag().toString());
                    if (i == headerColorRow) {
                        resetPref("chatHeaderColor");
                    } else if (i == memberColorRow) {
                        resetPref("chatMemberColor");
                    } else if (i == rTextColorRow) {
                        resetPref("chatRTextColor");
                    } else if (i == lTextColorRow) {
                        resetPref("chatLTextColor");
                    } else if (i == nameColorRow) {
                        resetPref("chatNameColor");
                    } else if (i == nameSizeRow) {
                        resetPref("chatNameSize");
                    } else if (i == statusColorRow) {
                        resetPref("chatStatusColor");
                    } else if (i == statusSizeRow) {
                        resetPref("chatStatusSize");
                    } else if (i == rTimeColorRow) {
                        resetPref("chatRTimeColor");
                    } else if (i == lTimeColorRow) {
                        resetPref("chatLTimeColor");
                    } else if (i == dateColorRow) {
                        resetPref("chatDateColor");
                    } else if (i == checksColorRow) {
                        resetPref("chatChecksColor");
                    } else if (i == textSizeRow) {
                        resetInt("chatTextSize", 16);
                    } else if (i == timeSizeRow) {
                        resetPref("chatTimeSize");
                    } else if (i == dateSizeRow) {
                        resetPref("chatDateSize");
                    } else if (i == dateBubbleColorRow) {
                        resetPref("chatDateBubbleColor");
                    } else if (i == sendColorRow) {
                        resetPref("chatSendIconColor");
                    } else if (i == editTextColorRow) {
                        resetPref("chatEditTextColor");
                    } else if (i == editTextSizeRow) {
                        resetPref("chatEditTextSize");
                    } else if (i == editTextBGColorRow) {
                        resetPref("chatEditTextBGColor");
                    } else if (i == emojiViewBGColorRow) {
                        resetPref("chatEmojiViewBGColor");
                    } else if (i == emojiViewTabColorRow) {
                        resetPref("chatEmojiViewTabColor");
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
    
    private void resetInt(String key, int value){
        resetPref(key);
        if(key.equals("chatTextSize")){
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("fons_size", value);
            MessagesController.getInstance().fontSize = value;
            editor.commit();
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
            return  i == headerColorRow || i == muteColorRow || i == headerIconsColorRow || i == rBubbleColorRow || i == lBubbleColorRow ||
                    i == avatarRadiusRow || i == nameColorRow || i == nameSizeRow || i == statusColorRow || i == statusSizeRow ||
                    i == textSizeRow || i == timeSizeRow || i == dateColorRow || i == dateSizeRow || i == dateBubbleColorRow || i == rTextColorRow || i == rLinkColorRow || i == lTextColorRow || i == lLinkColorRow ||
                    i == rTimeColorRow|| i == lTimeColorRow || i == checksColorRow || i == memberColorCheckRow || AndroidUtilities.getBoolPref("chatMemberColorCheck") && i == memberColorRow || i == forwardNameColorRow ||
                    i == editTextSizeRow || i == editTextColorRow || i == editTextIconsColorRow || i == sendColorRow || i == editTextBGColorRow ||
                    i == emojiViewBGColorRow || i == emojiViewTabColorRow;
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
                    ((HeaderCell) view).setText(LocaleController.getString("ChatList", R.string.ChatList));
                }
            }
            else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == avatarRadiusRow) {
                    textCell.setTag("chatAvatarRadius");
                    int size = themePrefs.getInt("chatAvatarRadius", AndroidUtilities.isTablet() ? 35 : 32);
                    textCell.setTextAndValue(LocaleController.getString("AvatarRadius", R.string.AvatarRadius), String.format("%d", size), true);
                } else if (i == nameSizeRow) {
                    int size = themePrefs.getInt("chatNameSize", AndroidUtilities.isTablet() ? 20 : 18);
                    textCell.setTextAndValue(LocaleController.getString("NameSize", R.string.NameSize), String.format("%d", size), true);
                } else if (i == statusSizeRow) {
                    int size = themePrefs.getInt("chatStatusSize", AndroidUtilities.isTablet() ? 16 : 14);
                    textCell.setTextAndValue(LocaleController.getString("StatusSize", R.string.StatusSize), String.format("%d", size), true);
                } else if (i == textSizeRow) {
                    int size = themePrefs.getInt("chatTextSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("TextSize", R.string.TextSize), String.format("%d", size), true);
                } else if (i == timeSizeRow) {
                    int size = themePrefs.getInt("chatTimeSize", AndroidUtilities.isTablet() ? 14 : 12);
                    textCell.setTextAndValue(LocaleController.getString("TimeSize", R.string.TimeSize), String.format("%d", size), true);
                } else if (i == dateSizeRow) {
                    int size = themePrefs.getInt("chatDateSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("DateSize", R.string.DateSize), String.format("%d", size), true);
                }  else if (i == editTextSizeRow) {
                    int size = themePrefs.getInt("chatEditTextSize", AndroidUtilities.isTablet() ? 20 : 18);
                    textCell.setTextAndValue(LocaleController.getString("EditTextSize", R.string.EditTextSize), String.format("%d", size), true);
                }
            } else if (type == 4) {
                if (view == null) {
                    view = new TextCheckCell(mContext);
                }
                TextCheckCell textCell = (TextCheckCell) view;
                if (i == memberColorCheckRow) {
                    textCell.setTag("chatMemberColorCheck");
                    textCell.setTextAndCheck(LocaleController.getString("SetMemberColor", R.string.SetMemberColor), themePrefs.getBoolean("chatMemberColorCheck", false), false);
                }
            }
            else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                int defColor = themePrefs.getInt("themeColor", AndroidUtilities.defColor);
                int darkColor = AndroidUtilities.getIntDarkerColor("themeColor", 0x15);
                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("chatHeaderColor", defColor), true);
                } else if (i == headerIconsColorRow) {
                    textCell.setTag("chatHeaderIconsColor");
                    textCell.setTextAndColor(LocaleController.getString("HeaderIconsColor", R.string.HeaderIconsColor), themePrefs.getInt(textCell.getTag().toString(), 0xffffffff), true);
                } else if (i == memberColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("MemberColor", R.string.MemberColor), AndroidUtilities.getBoolPref("chatMemberColorCheck") ? themePrefs.getInt("chatMemberColor", darkColor) : 0x00000000, true);
                } else if (i == forwardNameColorRow) {
                    textCell.setTag("chatForwardColor");
                    textCell.setTextAndColor(LocaleController.getString("ForwardNameColor", R.string.ForwardNameColor), themePrefs.getInt("chatForwardColor", darkColor), true);
                } else if (i == muteColorRow) {
                    textCell.setTag("chatMuteColor");
                    textCell.setTextAndColor(LocaleController.getString("MuteColor", R.string.MuteColor), themePrefs.getInt("chatMuteColor", 0xffffffff), true);
                } else if (i == rBubbleColorRow) {
                    textCell.setTag("chatRBubbleColor");
                    textCell.setTextAndColor(LocaleController.getString("RBubbleColor", R.string.RBubbleColor), themePrefs.getInt("chatRBubbleColor", AndroidUtilities.getDefBubbleColor()), true);
                } else if (i == lBubbleColorRow) {
                    textCell.setTag("chatLBubbleColor");
                    textCell.setTextAndColor(LocaleController.getString("LBubbleColor", R.string.LBubbleColor), themePrefs.getInt("chatLBubbleColor", 0xffffffff), true);
                } else if (i == rTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RTextColor", R.string.RTextColor), themePrefs.getInt("chatRTextColor", 0xff000000), true);
                } else if (i == lTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("LTextColor", R.string.LTextColor), themePrefs.getInt("chatLTextColor", 0xff000000), true);
                } else if (i == rLinkColorRow) {
                    textCell.setTag("chatRLinkColor");
                    textCell.setTextAndColor(LocaleController.getString("RLinkColor", R.string.RLinkColor), themePrefs.getInt("chatRLinkColor", defColor), true);
                } else if (i == lLinkColorRow) {
                    textCell.setTag("chatLLinkColor");
                    textCell.setTextAndColor(LocaleController.getString("LLinkColor", R.string.LLinkColor), themePrefs.getInt("chatLLinkColor", defColor), true);
                } else if (i == nameColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("chatNameColor", 0xffffffff), true);
                } else if (i == statusColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("StatusColor", R.string.StatusColor), themePrefs.getInt("chatStatusColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), true);
                } else if (i == rTimeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RTimeColor", R.string.RTimeColor), themePrefs.getInt("chatRTimeColor", darkColor), true);
                } else if (i == lTimeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("LTimeColor", R.string.LTimeColor), themePrefs.getInt("chatLTimeColor", 0xffa1aab3), true);
                } else if (i == checksColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ChecksColor", R.string.ChecksColor), themePrefs.getInt("chatChecksColor", defColor), true);
                } else if (i == dateColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("DateColor", R.string.DateColor), themePrefs.getInt("chatDateColor", 0xffffffff), true);
                } else if (i == dateBubbleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("DateBubbleColor", R.string.DateBubbleColor), themePrefs.getInt("chatDateBubbleColor", 0x59000000), true);
                } else if (i == sendColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("SendIcon", R.string.SendIcon), themePrefs.getInt("chatSendIconColor", themePrefs.getInt("chatEditTextIconsColor", defColor)), true);
                } else if (i == editTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EditTextColor", R.string.EditTextColor), themePrefs.getInt("chatEditTextColor", 0xff000000), true);
                } else if (i == editTextBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EditTextBGColor", R.string.EditTextBGColor), themePrefs.getInt("chatEditTextBGColor", 0xffffffff), true);
                } else if (i == editTextIconsColorRow) {
                    textCell.setTag("chatEditTextIconsColor");
                    textCell.setTextAndColor(LocaleController.getString("EditTextIconsColor", R.string.EditTextIconsColor), themePrefs.getInt("chatEditTextIconsColor", 0xffadadad), true);
                } else if (i == emojiViewBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EmojiViewBGColor", R.string.EmojiViewBGColor), themePrefs.getInt("chatEmojiViewBGColor", 0xff222222), true);
                } else if (i == emojiViewTabColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EmojiViewTabColor", R.string.EmojiViewTabColor), themePrefs.getInt("chatEmojiViewTabColor", darkColor), true);
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
            else if ( i == avatarRadiusRow || i == nameSizeRow ||  i == statusSizeRow || i == textSizeRow || i == timeSizeRow || i == dateSizeRow  || i == editTextSizeRow) {
                return 2;
            }

            else if ( i == headerColorRow  || i == muteColorRow || i == headerIconsColorRow ||
                    i == rBubbleColorRow || i == lBubbleColorRow || i == nameColorRow || i == statusColorRow || i == dateColorRow || i == dateBubbleColorRow ||
                    i == rTextColorRow || i == rLinkColorRow || i == lTextColorRow || i == lLinkColorRow || i == rLinkColorRow || i == rTimeColorRow || i == lTimeColorRow || i == checksColorRow || i == memberColorRow || i == forwardNameColorRow ||
                    i == sendColorRow || i == editTextColorRow || i == editTextBGColorRow || i == editTextIconsColorRow ||
                    i == emojiViewBGColorRow || i == emojiViewTabColorRow) {
                return 3;
            } else if (i == memberColorCheckRow) {
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

package org.telegram.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PhonebookShareActivity extends BaseFragment {

    public class TextCheckBoxCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView imageView;
        private CheckBoxSquare checkBox;

        public TextCheckBoxCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setSingleLine(false);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (isImport ? 17 : 64) : 71, 10, LocaleController.isRTL ? 71 : (isImport ? 17 : 64), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? (isImport ? 17 : 64) : 71, 35, LocaleController.isRTL ? 71 : (isImport ? 17 : 64), 0));

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 20, LocaleController.isRTL ? 16 : 0, 0));

            if (!isImport) {
                checkBox = new CheckBoxSquare(context, false);
                checkBox.setDuplicateParentStateEnabled(false);
                checkBox.setFocusable(false);
                checkBox.setFocusableInTouchMode(false);
                checkBox.setClickable(false);
                addView(checkBox, LayoutHelper.createFrame(18, 18, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 19, 0, 19, 0));
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (checkBox != null) {
                checkBox.invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measureChildWithMargins(textView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            measureChildWithMargins(valueTextView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            measureChildWithMargins(imageView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            if (checkBox != null) {
                measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(64), textView.getMeasuredHeight() + valueTextView.getMeasuredHeight() + AndroidUtilities.dp(10 + 10)));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int y = textView.getMeasuredHeight() + AndroidUtilities.dp(10 + 3);
            valueTextView.layout(valueTextView.getLeft(), y, valueTextView.getRight(), y + valueTextView.getMeasuredHeight());
        }

        public void setVCardItem(AndroidUtilities.VcardItem item, int icon) {
            textView.setText(item.getValue(true));
            valueTextView.setText(item.getType());
            if (checkBox != null) {
                checkBox.setChecked(item.checked, false);
            }
            if (icon != 0) {
                imageView.setImageResource(icon);
            } else {
                imageView.setImageDrawable(null);
            }
        }

        public void setChecked(boolean checked) {
            if (checkBox != null) {
                checkBox.setChecked(checked, true);
            }
        }

        public boolean isChecked() {
            return checkBox != null && checkBox.isChecked();
        }
    }

    private RecyclerListView listView;
    private ListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PhonebookSelectActivity.PhonebookSelectActivityDelegate delegate;
    private BackupImageView avatarImage;
    private TextView nameTextView;
    private View extraHeightView;
    private View shadowView;
    private FrameLayout bottomLayout;
    private TextView shareTextView;
    private ChatActivity parentFragment;

    private int extraHeight;

    private int rowCount;
    private int overscrollRow;
    private int emptyRow;
    private int phoneStartRow;
    private int phoneEndRow;
    private int phoneDividerRow;
    private int vcardStartRow;
    private int vcardEndRow;
    private int detailRow;

    private boolean isImport;

    private ArrayList<AndroidUtilities.VcardItem> other = new ArrayList<>();
    private ArrayList<AndroidUtilities.VcardItem> phones = new ArrayList<>();
    private TLRPC.User currentUser;

    public PhonebookShareActivity(ContactsController.Contact contact, Uri uri, File file, String name) {
        super();
        ArrayList<TLRPC.User> result = null;
        ArrayList<AndroidUtilities.VcardItem> items = new ArrayList<>();
        if (uri != null) {
            result = AndroidUtilities.loadVCardFromStream(uri, currentAccount, false, items, name);
        } else if (file != null) {
            result = AndroidUtilities.loadVCardFromStream(Uri.fromFile(file), currentAccount, false, items, name);
            file.delete();
            isImport = true;
        } else if (contact.key != null) {
            uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, contact.key);
            result = AndroidUtilities.loadVCardFromStream(uri, currentAccount, true, items, name);
        } else {
            currentUser = contact.user;
            AndroidUtilities.VcardItem item = new AndroidUtilities.VcardItem();
            item.type = 0;
            item.vcardData.add(item.fullData = "TEL;MOBILE:+" + currentUser.phone);
            phones.add(item);
        }

        if (result != null) {
            for (int a = 0; a < items.size(); a++) {
                AndroidUtilities.VcardItem item = items.get(a);
                if (item.type == 0) {
                    boolean exists = false;
                    for (int b = 0; b < phones.size(); b++) {
                        if (phones.get(b).getValue(false).equals(item.getValue(false))) {
                            exists = true;
                            break;
                        }
                    }
                    if (exists) {
                        item.checked = false;
                        continue;
                    }
                    phones.add(item);
                } else {
                    other.add(item);
                }
            }
            if (result != null && !result.isEmpty()) {
                currentUser = result.get(0);
                if (contact != null && contact.user != null) {
                    currentUser.photo = contact.user.photo;
                }
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (currentUser == null) {
            return false;
        }

        rowCount = 0;
        overscrollRow = rowCount++;
        emptyRow = rowCount++;
        if (phones.isEmpty()) {
            phoneStartRow = -1;
            phoneEndRow = -1;
        } else {
            phoneStartRow = rowCount;
            rowCount += phones.size();
            phoneEndRow = rowCount;
        }
        if (other.isEmpty()) {
            phoneDividerRow = -1;
            vcardStartRow = -1;
            vcardEndRow = -1;
        } else {
            if (phones.isEmpty()) {
                phoneDividerRow = -1;
            } else {
                phoneDividerRow = rowCount++;
            }
            vcardStartRow = rowCount;
            rowCount += other.size();
            vcardEndRow = rowCount;
        }
        detailRow = rowCount++;
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_avatar_actionBarIconBlue), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAddToContainer(false);
        extraHeight = 88;
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

        fragmentView = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == listView) {
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    if (parentLayout != null) {
                        int actionBarHeight = 0;
                        int childCount = getChildCount();
                        for (int a = 0; a < childCount; a++) {
                            View view = getChildAt(a);
                            if (view == child) {
                                continue;
                            }
                            if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                                if (((ActionBar) view).getCastShadows()) {
                                    actionBarHeight = view.getMeasuredHeight();
                                }
                                break;
                            }
                        }
                        parentLayout.drawHeaderShadow(canvas, actionBarHeight);
                    }
                    return result;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setGlowColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
        listView.setAdapter(new ListAdapter(context));
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setOnItemClickListener((view, position) -> {
            final AndroidUtilities.VcardItem item;
            if (position >= phoneStartRow && position < phoneEndRow) {
                item = phones.get(position - phoneStartRow);
            } else if (position >= vcardStartRow && position < vcardEndRow) {
                item = other.get(position - vcardStartRow);
            } else {
                item = null;
            }
            if (item == null) {
                return;
            }
            if (isImport) {
                if (item.type == 0) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.getValue(false)));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getParentActivity().startActivityForResult(intent, 500);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (item.type == 1) {
                    Browser.openUrl(getParentActivity(), "mailto:"  + item.getValue(false));
                } else if (item.type == 3) {
                    String url = item.getValue(false);
                    if (!url.startsWith("http")) {
                        url = "http://" + url;
                    }
                    Browser.openUrl(getParentActivity(), url);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
                        if (i == 0) {
                            try {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", item.getValue(false));
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                    showDialog(builder.create());
                }
            } else {
                item.checked = !item.checked;
                if (position >= phoneStartRow && position < phoneEndRow) {
                    boolean hasChecked = false;
                    for (int a = 0; a < phones.size(); a++) {
                        if (phones.get(a).checked) {
                            hasChecked = true;
                            break;
                        }
                    }
                    bottomLayout.setEnabled(hasChecked);
                    shareTextView.setAlpha(hasChecked ? 1.0f : 0.5f);
                }
                TextCheckBoxCell cell = (TextCheckBoxCell) view;
                cell.setChecked(item.checked);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            final AndroidUtilities.VcardItem item;
            if (position >= phoneStartRow && position < phoneEndRow) {
                item = phones.get(position - phoneStartRow);
            } else if (position >= vcardStartRow && position < vcardEndRow) {
                item = other.get(position - vcardStartRow);
            } else {
                item = null;
            }
            if (item == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
                if (i == 0) {
                    try {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", item.getValue(false));
                        clipboard.setPrimaryClip(clip);
                        if (item.type == 0) {
                            Toast.makeText(getParentActivity(), LocaleController.getString("PhoneCopied", R.string.PhoneCopied), Toast.LENGTH_SHORT).show();
                        } else if (item.type == 1) {
                            Toast.makeText(getParentActivity(), LocaleController.getString("EmailCopied", R.string.EmailCopied), Toast.LENGTH_SHORT).show();
                        } else if (item.type == 3) {
                            Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            showDialog(builder.create());
            return true;
        });

        frameLayout.addView(actionBar);

        extraHeightView = new View(context);
        extraHeightView.setPivotY(0);
        extraHeightView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
        frameLayout.addView(extraHeightView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 88));

        shadowView = new View(context);
        shadowView.setBackgroundResource(R.drawable.header_shadow);
        frameLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarImage.setPivotX(0);
        avatarImage.setPivotY(0);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.LEFT, 64, 0, 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_profile_title));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setPivotX(0);
        nameTextView.setPivotY(0);
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 118, 8, 10, 0));

        needLayout();

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (layoutManager.getItemCount() == 0) {
                    return;
                }
                int height = 0;
                View child = recyclerView.getChildAt(0);
                if (child != null) {
                    if (layoutManager.findFirstVisibleItemPosition() == 0) {
                        height = AndroidUtilities.dp(88) + (child.getTop() < 0 ? child.getTop() : 0);
                    }
                    if (extraHeight != height) {
                        extraHeight = height;
                        needLayout();
                    }
                }
            }
        });

        bottomLayout = new FrameLayout(context);
        bottomLayout.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_passport_authorizeBackground), Theme.getColor(Theme.key_passport_authorizeBackgroundSelected)));
        frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
        bottomLayout.setOnClickListener(v -> {
            if (isImport) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("CreateNewContact", R.string.CreateNewContact),
                        LocaleController.getString("AddToExistingContact", R.string.AddToExistingContact)
                }, new DialogInterface.OnClickListener() {

                    private void fillRowWithType(String type, ContentValues row) {
                        if (type.startsWith("X-")) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM);
                            row.put(ContactsContract.CommonDataKinds.Phone.LABEL, type.substring(2));
                        } else if ("PREF".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MAIN);
                        } else if ("HOME".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME);
                        } else if ("MOBILE".equalsIgnoreCase(type) || "CELL".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
                        } else if ("OTHER".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER);
                        } else if ("WORK".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK);
                        } else if ("RADIO".equalsIgnoreCase(type) || "VOICE".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_RADIO);
                        } else if ("PAGER".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_PAGER);
                        } else if ("CALLBACK".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK);
                        } else if ("CAR".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CAR);
                        } else if ("ASSISTANT".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT);
                        } else if ("MMS".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MMS);
                        } else if (type.startsWith("FAX")) {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK);
                        } else {
                            row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM);
                            row.put(ContactsContract.CommonDataKinds.Phone.LABEL, type);
                        }
                    }

                    private void fillUrlRowWithType(String type, ContentValues row) {
                        if (type.startsWith("X-")) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM);
                            row.put(ContactsContract.CommonDataKinds.Website.LABEL, type.substring(2));
                        } else if ("HOMEPAGE".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE);
                        } else if ("BLOG".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_BLOG);
                        } else if ("PROFILE".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_PROFILE);
                        } else if ("HOME".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOME);
                        } else if ("WORK".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_WORK);
                        } else if ("FTP".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_FTP);
                        } else if ("OTHER".equalsIgnoreCase(type)) {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_OTHER);
                        } else {
                            row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM);
                            row.put(ContactsContract.CommonDataKinds.Website.LABEL, type);
                        }
                    }

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        Intent intent = null;
                        if (which == 0) {
                            intent = new Intent(ContactsContract.Intents.Insert.ACTION);
                            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
                        } else if (which == 1) {
                            intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                        }

                        intent.putExtra(ContactsContract.Intents.Insert.NAME, ContactsController.formatName(currentUser.first_name, currentUser.last_name));

                        ArrayList<ContentValues> data = new ArrayList<>();

                        for (int a = 0; a < phones.size(); a++) {
                            AndroidUtilities.VcardItem item = phones.get(a);

                            ContentValues row = new ContentValues();
                            row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                            row.put(ContactsContract.CommonDataKinds.Phone.NUMBER, item.getValue(false));

                            String type = item.getRawType(false);
                            fillRowWithType(type, row);
                            data.add(row);
                        }

                        boolean orgAdded = false;
                        for (int a = 0; a < other.size(); a++) {
                            AndroidUtilities.VcardItem item = other.get(a);

                            if (item.type == 1) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
                                row.put(ContactsContract.CommonDataKinds.Email.ADDRESS, item.getValue(false));
                                String type = item.getRawType(false);
                                fillRowWithType(type, row);
                                data.add(row);
                            } else if (item.type == 3) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
                                row.put(ContactsContract.CommonDataKinds.Website.URL, item.getValue(false));
                                String type = item.getRawType(false);
                                fillUrlRowWithType(type, row);
                                data.add(row);
                            } else if (item.type == 4) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);
                                row.put(ContactsContract.CommonDataKinds.Note.NOTE, item.getValue(false));
                                data.add(row);
                            } else if (item.type == 5) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
                                row.put(ContactsContract.CommonDataKinds.Event.START_DATE, item.getValue(false));
                                row.put(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY);
                                data.add(row);
                            } else if (item.type == 2) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
                                String[] args = item.getRawValue();
                                if (args.length > 0) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX, args[0]);
                                }
                                if (args.length > 1) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD, args[1]);
                                }
                                if (args.length > 2) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET, args[2]);
                                }
                                if (args.length > 3) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY, args[3]);
                                }
                                if (args.length > 4) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION, args[4]);
                                }
                                if (args.length > 5) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, args[5]);
                                }
                                if (args.length > 6) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, args[6]);
                                }

                                String type = item.getRawType(false);
                                if ("HOME".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME);
                                } else if ("WORK".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK);
                                } else if ("OTHER".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER);
                                }
                                data.add(row);
                            } else if (item.type == 20) {
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
                                String imType = item.getRawType(true);
                                String type = item.getRawType(false);
                                row.put(ContactsContract.CommonDataKinds.Im.DATA, item.getValue(false));
                                if ("AIM".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM);
                                } else if ("MSN".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN);
                                } else if ("YAHOO".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO);
                                } else if ("SKYPE".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE);
                                } else if ("QQ".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ);
                                } else if ("GOOGLE-TALK".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK);
                                } else if ("ICQ".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ);
                                } else if ("JABBER".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER);
                                } else if ("NETMEETING".equalsIgnoreCase(imType)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING);
                                } else {
                                    row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM);
                                    row.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, item.getRawType(true));
                                }
                                if ("HOME".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_HOME);
                                } else if ("WORK".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_WORK);
                                } else if ("OTHER".equalsIgnoreCase(type)) {
                                    row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_OTHER);
                                }
                                data.add(row);
                            } else if (item.type == 6) {
                                if (orgAdded) {
                                    continue;
                                }
                                orgAdded = true;
                                ContentValues row = new ContentValues();
                                row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
                                for (int b = a; b < other.size(); b++) {
                                    AndroidUtilities.VcardItem orgItem = other.get(b);
                                    if (orgItem.type != 6) {
                                        continue;
                                    }
                                    String type = orgItem.getRawType(true);
                                    if ("ORG".equalsIgnoreCase(type)) {
                                        String[] value = orgItem.getRawValue();
                                        if (value.length == 0) {
                                            continue;
                                        }
                                        if (value.length >= 1) {
                                            row.put(ContactsContract.CommonDataKinds.Organization.COMPANY, value[0]);
                                        }
                                        if (value.length >= 2) {
                                            row.put(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, value[1]);
                                        }
                                    } else if ("TITLE".equalsIgnoreCase(type)) {
                                        row.put(ContactsContract.CommonDataKinds.Organization.TITLE, orgItem.getValue(false));
                                    } else if ("ROLE".equalsIgnoreCase(type)) {
                                        row.put(ContactsContract.CommonDataKinds.Organization.TITLE, orgItem.getValue(false));
                                    }

                                    String orgType = orgItem.getRawType(true);
                                    if ("WORK".equalsIgnoreCase(orgType)) {
                                        row.put(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK);
                                    } else if ("OTHER".equalsIgnoreCase(orgType)) {
                                        row.put(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_OTHER);
                                    }
                                }
                                data.add(row);
                            }
                        }

                        intent.putExtra("finishActivityOnSaveCompleted", true);
                        intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);

                        try {
                            getParentActivity().startActivity(intent);
                            finishFragment();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
                builder.show();
            } else {
                StringBuilder builder;
                if (!currentUser.restriction_reason.isEmpty()) {
                    builder = new StringBuilder(currentUser.restriction_reason.get(0).text);
                } else {
                    builder = new StringBuilder(String.format(Locale.US, "BEGIN:VCARD\nVERSION:3.0\nFN:%1$s\nEND:VCARD", ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
                }
                int idx = builder.lastIndexOf("END:VCARD");
                if (idx >= 0) {
                    currentUser.phone = null;
                    for (int a = phones.size() - 1; a >= 0; a--) {
                        AndroidUtilities.VcardItem item = phones.get(a);
                        if (!item.checked) {
                            continue;
                        }
                        if (currentUser.phone == null) {
                            currentUser.phone = item.getValue(false);
                        }
                        for (int b = 0; b < item.vcardData.size(); b++) {
                            builder.insert(idx, item.vcardData.get(b) + "\n");
                        }
                    }
                    for (int a = other.size() - 1; a >= 0; a--) {
                        AndroidUtilities.VcardItem item = other.get(a);
                        if (!item.checked) {
                            continue;
                        }
                        for (int b = item.vcardData.size() - 1; b >= 0; b--) {
                            builder.insert(idx, item.vcardData.get(b) + "\n");
                        }
                    }
                    TLRPC.TL_restrictionReason reason = new TLRPC.TL_restrictionReason();
                    reason.text = builder.toString();
                    reason.reason = "";
                    reason.platform = "";
                    currentUser.restriction_reason.add(reason);
                }
                if (parentFragment != null && parentFragment.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> {
                        delegate.didSelectContact(currentUser, notify, scheduleDate);
                        finishFragment();
                    });
                } else {
                    delegate.didSelectContact(currentUser, true, 0);
                    finishFragment();
                }
            }
        });

        shareTextView = new TextView(context);
        shareTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        shareTextView.setTextColor(Theme.getColor(Theme.key_passport_authorizeText));
        if (isImport) {
            shareTextView.setText(LocaleController.getString("AddContactChat", R.string.AddContactChat));
        } else {
            shareTextView.setText(LocaleController.getString("ContactShare", R.string.ContactShare));
        }
        shareTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        shareTextView.setGravity(Gravity.CENTER);
        shareTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomLayout.addView(shareTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setProfile(true);
        avatarDrawable.setInfo(5, currentUser.first_name, currentUser.last_name);
        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));

        avatarImage.setImage(ImageLocation.getForUser(currentUser, false), "50_50", avatarDrawable, currentUser);
        nameTextView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));

        return fragmentView;
    }

    public void setChatActivity(ChatActivity chatActivity) {
        parentFragment = chatActivity;
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    public void setDelegate(PhonebookSelectActivity.PhonebookSelectActivityDelegate phonebookSelectActivityDelegate) {
        delegate = phonebookSelectActivityDelegate;
    }

    private void needLayout() {
        FrameLayout.LayoutParams layoutParams;
        int newTop = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
        if (listView != null) {
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            if (layoutParams.topMargin != newTop) {
                layoutParams.topMargin = newTop;
                listView.setLayoutParams(layoutParams);
                extraHeightView.setTranslationY(newTop);
            }
        }

        if (avatarImage != null) {
            float diff = extraHeight / (float) AndroidUtilities.dp(88);
            extraHeightView.setScaleY(diff);
            shadowView.setTranslationY(newTop + extraHeight);

            avatarImage.setScaleX((42 + 18 * diff) / 42.0f);
            avatarImage.setScaleY((42 + 18 * diff) / 42.0f);
            float avatarY = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight() / 2.0f * (1.0f + diff) - 21 * AndroidUtilities.density + 27 * AndroidUtilities.density * diff;
            avatarImage.setTranslationX(-AndroidUtilities.dp(47) * diff);
            avatarImage.setTranslationY((float) Math.ceil(avatarY));
            nameTextView.setTranslationX(-21 * AndroidUtilities.density * diff);
            nameTextView.setTranslationY((float) Math.floor(avatarY) - (float) Math.ceil(AndroidUtilities.density) + (float) Math.floor(7 * AndroidUtilities.density * diff));
            nameTextView.setScaleX(1.0f + 0.12f * diff);
            nameTextView.setScaleY(1.0f + 0.12f * diff);
        }
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return true;
            }
        });
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
                    if (position == overscrollRow) {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(88));
                    } else {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(16));
                    }
                    break;
                }
                case 1: {
                    TextCheckBoxCell cell = (TextCheckBoxCell) holder.itemView;
                    AndroidUtilities.VcardItem item;
                    int icon;
                    if (position >= phoneStartRow && position < phoneEndRow) {
                        item = phones.get(position - phoneStartRow);
                        if (position == phoneStartRow) {
                            icon = R.drawable.profile_phone;
                        } else {
                            icon = 0;
                        }
                    } else {
                        item = other.get(position - vcardStartRow);
                        if (position == vcardStartRow) {
                            icon = R.drawable.profile_info;
                        } else {
                            icon = 0;
                        }
                    }
                    cell.setVCardItem(item, icon);
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= phoneStartRow && position < phoneEndRow || position >= vcardStartRow && position < vcardEndRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new EmptyCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckBoxCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new DividerCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view.setPadding(AndroidUtilities.dp(72), AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == emptyRow || position == overscrollRow) {
                return 0;
            } else if (position >= phoneStartRow && position < phoneEndRow || position >= vcardStartRow && position < vcardEndRow) {
                return 1;
            } else if (position == phoneDividerRow) {
                return 2;
            } else if (position == detailRow) {
                return 3;
            } else {
                return 2;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(shareTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_passport_authorizeText),
                new ThemeDescription(bottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_passport_authorizeBackground),
                new ThemeDescription(bottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_passport_authorizeBackgroundSelected),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareUnchecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareDisabled),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareBackground),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareCheck),
        };
    }
}

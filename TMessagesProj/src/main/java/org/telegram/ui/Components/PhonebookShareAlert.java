/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import androidx.core.widget.NestedScrollView;

public class PhonebookShareAlert extends BottomSheet {

    private ListAdapter listAdapter;
    private NestedScrollView scrollView;
    private LinearLayout linearLayout;
    private ActionBar actionBar;
    private View actionBarShadow;
    private View shadow;
    private TextView buttonTextView;

    private BaseFragment parentFragment;

    private boolean inLayout;

    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int scrollOffsetY;
    private AnimatorSet actionBarAnimation;
    private AnimatorSet shadowAnimation;

    private int rowCount;
    private int userRow;
    private int phoneStartRow;
    private int phoneEndRow;
    private int vcardStartRow;
    private int vcardEndRow;

    private boolean isImport;

    private ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate delegate;

    private ArrayList<AndroidUtilities.VcardItem> other = new ArrayList<>();
    private ArrayList<AndroidUtilities.VcardItem> phones = new ArrayList<>();
    private TLRPC.User currentUser;

    public class UserCell extends LinearLayout {

        public UserCell(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);

            String status;
            boolean needPadding = true;
            if (phones.size() == 1 && other.size() == 0) {
                status = phones.get(0).getValue(true);
                needPadding = false;
            } else if (currentUser.status != null && currentUser.status.expires != 0) {
                status = LocaleController.formatUserStatus(currentAccount, currentUser);
            } else {
                status = null;
            }

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(30));
            avatarDrawable.setInfo(currentUser);

            BackupImageView avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(40));
            avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
            addView(avatarImageView, LayoutHelper.createLinear(80, 80, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

            TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setText(ContactsController.formatName(currentUser.first_name, currentUser.last_name));
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 10, 10, status != null ? 0 : 27));

            if (status != null) {
                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setText(status);
                addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 3, 10, needPadding ? 27 : 11));
            }
        }
    }

    public class TextCheckBoxCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView imageView;
        private Switch checkBox;
        private boolean needDivider;

        public TextCheckBoxCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setSingleLine(false);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? (isImport ? 17 : 64) : 72, 10, LocaleController.isRTL ? 72 : (isImport ? 17 : 64), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, LocaleController.isRTL ? (isImport ? 17 : 64) : 72, 35, LocaleController.isRTL ? 72 : (isImport ? 17 : 64), 0));

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 20, 20, LocaleController.isRTL ? 20 : 0, 0));

            if (!isImport) {
                checkBox = new Switch(context);
                checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
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
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(64), textView.getMeasuredHeight() + valueTextView.getMeasuredHeight() + AndroidUtilities.dp(10 + 10)) + (needDivider ? 1 : 0));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int y = textView.getMeasuredHeight() + AndroidUtilities.dp(10 + 3);
            valueTextView.layout(valueTextView.getLeft(), y, valueTextView.getRight(), y + valueTextView.getMeasuredHeight());
        }

        public void setVCardItem(AndroidUtilities.VcardItem item, int icon, boolean divider) {
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
            needDivider = divider;
            setWillNotDraw(!needDivider);
        }

        public void setChecked(boolean checked) {
            if (checkBox != null) {
                checkBox.setChecked(checked, true);
            }
        }

        public boolean isChecked() {
            return checkBox != null && checkBox.isChecked();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    public PhonebookShareAlert(BaseFragment parent, ContactsController.Contact contact, TLRPC.User user, Uri uri, File file, String firstName, String lastName) {
        this(parent, contact, user, uri, file, firstName, lastName, null);
    }

    public PhonebookShareAlert(BaseFragment parent, ContactsController.Contact contact, TLRPC.User user, Uri uri, File file, String firstName, String lastName, Theme.ResourcesProvider resourcesProvider) {
        super(parent.getParentActivity(), false, resourcesProvider);

        String name = ContactsController.formatName(firstName, lastName);
        ArrayList<TLRPC.User> result = null;
        ArrayList<AndroidUtilities.VcardItem> items = new ArrayList<>();
        ArrayList<TLRPC.TL_restrictionReason> vcard = null;
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
            AndroidUtilities.VcardItem item = new AndroidUtilities.VcardItem();
            item.type = 0;
            item.vcardData.add(item.fullData = "TEL;MOBILE:+" + contact.user.phone);
            phones.add(item);
        }
        if (user == null && contact != null) {
            user = contact.user;
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
            if (!result.isEmpty()) {
                TLRPC.User u = result.get(0);
                vcard = u.restriction_reason;
                if (TextUtils.isEmpty(firstName)) {
                    firstName = u.first_name;
                    lastName = u.last_name;
                }
            }
        }
        currentUser = new TLRPC.TL_userContact_old2();
        if (user != null) {
            currentUser.id = user.id;
            currentUser.access_hash = user.access_hash;
            currentUser.photo = user.photo;
            currentUser.status = user.status;
            currentUser.first_name = user.first_name;
            currentUser.last_name = user.last_name;
            currentUser.phone = user.phone;
            if (vcard != null) {
                currentUser.restriction_reason = vcard;
            }
        } else {
            currentUser.first_name = firstName;
            currentUser.last_name = lastName;
        }

        parentFragment = parent;
        Context context = parentFragment.getParentActivity();
        updateRows();

        FrameLayout frameLayout = new FrameLayout(context) {

            private RectF rect = new RectF();
            private boolean ignoreLayout;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY && actionBar.getAlpha() == 0.0f) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();

                int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2;

                LayoutParams layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                ignoreLayout = true;

                int padding;
                int contentSize = AndroidUtilities.dp(80);

                int count = listAdapter.getItemCount();
                for (int a = 0; a < count; a++) {
                    View view = listAdapter.createView(context, a);
                    view.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    contentSize += view.getMeasuredHeight();
                }
                if (contentSize < availableHeight) {
                    padding = availableHeight - contentSize;
                } else {
                    padding = availableHeight / 5;
                }
                if (scrollView.getPaddingTop() != padding) {
                    int diff = scrollView.getPaddingTop() - padding;
                    scrollView.setPadding(0, padding, 0, 0);
                }
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                inLayout = true;
                super.onLayout(changed, l, t, r, b);
                inLayout = false;
                updateLayout(false);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int top = scrollOffsetY - backgroundPaddingTop;

                int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                float rad = 1.0f;

                float r = AndroidUtilities.dp(12);
                if (top + backgroundPaddingTop < r) {
                    rad = 1.0f - Math.min(1.0f, (r - top - backgroundPaddingTop) / r);
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (rad != 1.0f) {
                    backgroundPaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, r * rad, r * rad, backgroundPaint);
                }

                int color1 = getThemedColor(Theme.key_dialogBackground);
                int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                backgroundPaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaint);
            }
        };
        frameLayout.setWillNotDraw(false);
        containerView = frameLayout;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        listAdapter = new ListAdapter();

        scrollView = new NestedScrollView(context) {

            private View focusingView;

            @Override
            public void requestChildFocus(View child, View focused) {
                focusingView = focused;
                super.requestChildFocus(child, focused);
            }

            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                if (focusingView == null || linearLayout.getTop() != getPaddingTop()) {
                    return 0;
                }
                int delta = super.computeScrollDeltaToGetChildRectOnScreen(rect);
                int currentViewY = focusingView.getTop() - getScrollY() + rect.top + delta;
                int diff = ActionBar.getCurrentActionBarHeight() - currentViewY;
                if (diff > 0) {
                    delta -= diff + AndroidUtilities.dp(10);
                }
                return delta;
            }
        };
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 77));
        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateLayout(!inLayout));

        for (int a = 0, N = listAdapter.getItemCount(); a < N; a++) {
            View view = listAdapter.createView(context, a);
            final int position = a;
            linearLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            if (position >= phoneStartRow && position < phoneEndRow || position >= vcardStartRow && position < vcardEndRow) {
                view.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.setOnClickListener(v -> {
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
                                this.parentFragment.getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else if (item.type == 1) {
                            Browser.openUrl(this.parentFragment.getParentActivity(), "mailto:"  + item.getValue(false));
                        } else if (item.type == 3) {
                            String url = item.getValue(false);
                            if (!url.startsWith("http")) {
                                url = "http://" + url;
                            }
                            Browser.openUrl(this.parentFragment.getParentActivity(), url);
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this.parentFragment.getParentActivity());
                            builder.setItems(new CharSequence[]{LocaleController.getString("Copy", R.string.Copy)}, (dialogInterface, i) -> {
                                if (i == 0) {
                                    try {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", item.getValue(false));
                                        clipboard.setPrimaryClip(clip);
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                            Toast.makeText(this.parentFragment.getParentActivity(), LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                                        }
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                            });
                            builder.show();
                        }
                    } else {
                        item.checked = !item.checked;
                        if (position >= phoneStartRow && position < phoneEndRow) {
                            boolean hasChecked = false;
                            for (int b = 0; b < phones.size(); b++) {
                                if (phones.get(b).checked) {
                                    hasChecked = true;
                                    break;
                                }
                            }
                            int color = getThemedColor(Theme.key_featuredStickers_buttonText);
                            buttonTextView.setEnabled(hasChecked);
                            buttonTextView.setTextColor(hasChecked ? color : (color & 0x7fffffff));
                        }
                        TextCheckBoxCell cell = (TextCheckBoxCell) view;
                        cell.setChecked(item.checked);
                    }
                });
                view.setOnLongClickListener(v -> {
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
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", item.getValue(false));
                    clipboard.setPrimaryClip(clip);
                    if (BulletinFactory.canShowBulletin(parentFragment)) {
                        if (item.type == 3) {
                            BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyLinkBulletin().show();
                        } else {
                            final Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(context, resourcesProvider);
                            if (item.type == 0) {
                                layout.textView.setText(LocaleController.getString("PhoneCopied", R.string.PhoneCopied));
                                layout.imageView.setImageResource(R.drawable.menu_calls);
                            } else if (item.type == 1) {
                                layout.textView.setText(LocaleController.getString("EmailCopied", R.string.EmailCopied));
                                layout.imageView.setImageResource(R.drawable.menu_mail);
                            } else {
                                layout.textView.setText(LocaleController.getString("TextCopied", R.string.TextCopied));
                                layout.imageView.setImageResource(R.drawable.menu_info);
                            }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                Bulletin.make((FrameLayout) containerView, layout, Bulletin.DURATION_SHORT).show();
                            }
                        }
                    }
                    return true;
                });
            }
        }

        actionBar = new ActionBar(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
        actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(getThemedColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_dialogTextBlack));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);
        if (isImport) {
            actionBar.setTitle(LocaleController.getString("AddContactPhonebookTitle", R.string.AddContactPhonebookTitle));
        } else {
            actionBar.setTitle(LocaleController.getString("ShareContactTitle", R.string.ShareContactTitle));
        }
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss();
                }
            }
        });

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        shadow = new View(context);
        shadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 77));

        buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        if (isImport) {
            buttonTextView.setText(LocaleController.getString("AddContactPhonebookTitle", R.string.AddContactPhonebookTitle));
        } else {
            buttonTextView.setText(LocaleController.getString("ShareContactTitle", R.string.ShareContactTitle));
        }
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        frameLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.LEFT | Gravity.BOTTOM, 16, 16, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            if (isImport) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
                            PhonebookShareAlert.this.parentFragment.getParentActivity().startActivity(intent);
                            dismiss();
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
                    currentUser.restriction_reason.clear();
                    TLRPC.TL_restrictionReason reason = new TLRPC.TL_restrictionReason();
                    reason.text = builder.toString();
                    reason.reason = "";
                    reason.platform = "";
                    currentUser.restriction_reason.add(reason);
                }
                if (parentFragment instanceof ChatActivity && ((ChatActivity) parentFragment).isInScheduleMode()) {
                    ChatActivity chatActivity = (ChatActivity) parentFragment;
                    AlertsCreator.createScheduleDatePickerDialog(getContext(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                        delegate.didSelectContact(currentUser, notify, scheduleDate);
                        dismiss();
                    }, resourcesProvider);
                } else {
                    delegate.didSelectContact(currentUser, true, 0);
                    dismiss();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bulletin.addDelegate((FrameLayout) containerView, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.dp(74);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        Bulletin.removeDelegate((FrameLayout) containerView);
    }

    public void setDelegate(ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate phonebookShareAlertDelegate) {
        delegate = phonebookShareAlertDelegate;
    }

    private void updateLayout(boolean animated) {
        View child = scrollView.getChildAt(0);
        int top = child.getTop() - scrollView.getScrollY();
        int newOffset = 0;
        if (top >= 0) {
            newOffset = top;
        }
        boolean show = newOffset <= 0;
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            if (animated) {
                actionBarAnimation = new AnimatorSet();
                actionBarAnimation.setDuration(180);
                actionBarAnimation.playTogether(
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
                actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        actionBarAnimation = null;
                    }
                });
                actionBarAnimation.start();
            } else {
                actionBar.setAlpha(show ? 1.0f : 0.0f);
                actionBarShadow.setAlpha(show ? 1.0f : 0.0f);
            }
        }
        if (scrollOffsetY != newOffset) {
            scrollOffsetY = newOffset;
            containerView.invalidate();
        }

        int b = child.getBottom();
        int h = scrollView.getMeasuredHeight();
        show = child.getBottom() - scrollView.getScrollY() > scrollView.getMeasuredHeight();
        if (show && shadow.getTag() == null || !show && shadow.getTag() != null) {
            shadow.setTag(show ? 1 : null);
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
                shadowAnimation = null;
            }
            if (animated) {
                shadowAnimation = new AnimatorSet();
                shadowAnimation.setDuration(180);
                shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
                shadowAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        shadowAnimation = null;
                    }
                });
                shadowAnimation.start();
            } else {
                shadow.setAlpha(show ? 1.0f : 0.0f);
            }
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateRows() {
        rowCount = 0;
        userRow = rowCount++;
        if (phones.size() <= 1 && other.isEmpty()) {
            phoneStartRow = -1;
            phoneEndRow = -1;
            vcardStartRow = -1;
            vcardEndRow = -1;
        } else {
            if (phones.isEmpty()) {
                phoneStartRow = -1;
                phoneEndRow = -1;
            } else {
                phoneStartRow = rowCount;
                rowCount += phones.size();
                phoneEndRow = rowCount;
            }
            if (other.isEmpty()) {
                vcardStartRow = -1;
                vcardEndRow = -1;
            } else {
                vcardStartRow = rowCount;
                rowCount += other.size();
                vcardEndRow = rowCount;
            }
        }
    }

    private class ListAdapter {

        public int getItemCount() {
            return rowCount;
        }

        public void onBindViewHolder(View itemView, int position, int type) {
            if (type == 1) {
                TextCheckBoxCell cell = (TextCheckBoxCell) itemView;
                AndroidUtilities.VcardItem item;
                int icon;
                if (position >= phoneStartRow && position < phoneEndRow) {
                    item = phones.get(position - phoneStartRow);
                    icon = R.drawable.menu_calls;
                } else {
                    item = other.get(position - vcardStartRow);
                    if (item.type == 1) {
                        icon = R.drawable.menu_mail;
                    } else if (item.type == 2) {
                        icon = R.drawable.menu_location;
                    } else if (item.type == 3) {
                        icon = R.drawable.msg_link;
                    } else if (item.type == 4) {
                        icon = R.drawable.profile_info;
                    } else if (item.type == 5) {
                        icon = R.drawable.menu_date;
                    } else if (item.type == 6) {
                        if ("ORG".equalsIgnoreCase(item.getRawType(true))) {
                            icon = R.drawable.menu_work;
                        } else {
                            icon = R.drawable.menu_jobtitle;
                        }
                    } else if (item.type == 20) {
                        icon = R.drawable.menu_info;
                    } else {
                        icon = R.drawable.menu_info;
                    }
                }
                cell.setVCardItem(item, icon, position != getItemCount() - 1);
            }
        }

        public View createView(Context context, int position) {
            int viewType = getItemViewType(position);
            View view;
            switch (viewType) {
                case 0:
                    view = new UserCell(context);
                    break;
                case 1:
                default:
                    view = new TextCheckBoxCell(context);
                    break;
            }
            onBindViewHolder(view, position, viewType);
            return view;
        }

        public int getItemViewType(int position) {
            if (position == userRow) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}

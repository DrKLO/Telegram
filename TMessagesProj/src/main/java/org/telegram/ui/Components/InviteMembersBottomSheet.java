package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GroupCreateSectionCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class InviteMembersBottomSheet extends UsersAlertBase implements NotificationCenter.NotificationCenterDelegate {

    private LongSparseArray<TLObject> ignoreUsers;
    private final SpansContainer spansContainer;
    private final ScrollView spansScrollView;
    private SearchAdapter searchAdapter;

    private int emptyRow;
    private int copyLinkRow;
    private int contactsStartRow;
    private int contactsEndRow;
    private int noContactsStubRow;
    private int lastRow;

    private int rowCount;

    private AnimatorSet currentAnimation;

    private ArrayList<TLObject> contacts = new ArrayList<>();
    private LongSparseArray<GroupCreateSpan> selectedContacts = new LongSparseArray<>();

    private boolean spanEnter;
    private float spansEnterProgress = 0;
    private ValueAnimator spansEnterAnimator;
    private GroupCreateSpan currentDeletingSpan;
    private int scrollViewH;
    private GroupCreateActivity.ContactsAddActivityDelegate delegate;
    private InviteMembersBottomSheetDelegate dialogsDelegate;
    private ArrayList<TLRPC.Dialog> dialogsServerOnly;

    private int additionalHeight;

    private float touchSlop;
    private BaseFragment parentFragment;

    private View.OnClickListener spanClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            GroupCreateSpan span = (GroupCreateSpan) v;
            if (span.isDeleting()) {
                currentDeletingSpan = null;
                selectedContacts.remove(span.getUid());
                spansContainer.removeSpan(span);
                spansCountChanged(true);
                AndroidUtilities.updateVisibleRows(listView);
            } else {
                if (currentDeletingSpan != null) {
                    currentDeletingSpan.cancelDeleteAnimation();
                }
                currentDeletingSpan = span;
                span.startDeleteAnimation();
            }
        }
    };
    private int maxSize;
    private final ImageView floatingButton;
    private AnimatorSet currentDoneButtonAnimation;
    private int searchAdditionalHeight;
    private long chatId;

    public interface InviteMembersBottomSheetDelegate {
        void didSelectDialogs(ArrayList<Long> dids);
    }

    public InviteMembersBottomSheet(Context context, int account, LongSparseArray<TLObject> ignoreUsers, long chatId, BaseFragment parentFragment, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, account, resourcesProvider);
        this.ignoreUsers = ignoreUsers;
        needSnapToTop = false;
        this.parentFragment = parentFragment;
        this.chatId = chatId;

        searchView.searchEditText.setHint(LocaleController.getString("SearchForChats", R.string.SearchForChats));

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();

        searchListViewAdapter = searchAdapter = new SearchAdapter();
        listView.setAdapter(listViewAdapter = new ListAdapter());

        ArrayList<TLRPC.TL_contact> arrayList = ContactsController.getInstance(account).contacts;
        for (int a = 0; a < arrayList.size(); a++) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(arrayList.get(a).user_id);
            if (user == null || user.self || user.deleted) {
                continue;
            }
            contacts.add(user);
        }

        spansContainer = new SpansContainer(context);

        listView.setOnItemClickListener((view, position) -> {
            TLObject object = null;
            if (listView.getAdapter() == searchAdapter) {
                int localCount = searchAdapter.searchResult.size();
                int globalCount = searchAdapter.searchAdapterHelper.getGlobalSearch().size();
                int localServerCount = searchAdapter.searchAdapterHelper.getLocalServerSearch().size();

                position--;
                if (position >= 0 && position < localCount) {
                    object = (TLObject) searchAdapter.searchResult.get(position);
                } else if (position >= localCount && position < localServerCount + localCount) {
                    object = searchAdapter.searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                } else if (position > localCount + localServerCount && position <= globalCount + localCount + localServerCount) {
                    object = searchAdapter.searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount - 1);
                }
                if (dialogsDelegate != null) {
                    searchView.closeSearch();
                }
            } else {
                if (position == copyLinkRow) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
                    TLRPC.ChatFull chatInfo = MessagesController.getInstance(currentAccount).getChatFull(chatId);
                    String link = null;
                    if (chat != null && !TextUtils.isEmpty(chat.username)) {
                        link = "https://t.me/" + chat.username;
                    } else if (chatInfo != null &&  chatInfo.exported_invite != null) {
                        link = chatInfo.exported_invite.link;
                    } else {
                        generateLink();
                    }

                    if (link == null) {
                        return;
                    }
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", link);
                    clipboard.setPrimaryClip(clip);
                    dismiss();
                    BulletinFactory.createCopyLinkBulletin(parentFragment).show();

                } else if (position >= contactsStartRow && position < contactsEndRow) {
                    object = ((ListAdapter) listViewAdapter).getObject(position);
                }
            }

            if (object != null) {
                long id;
                if (object instanceof TLRPC.User) {
                    id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = 0;
                }
                if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                    return;
                }
                if (id != 0) {
                    if (selectedContacts.indexOfKey(id) >= 0) {
                        GroupCreateSpan groupCreateSpan = selectedContacts.get(id);
                        selectedContacts.remove(id);
                        spansContainer.removeSpan(groupCreateSpan);
                    } else {
                        GroupCreateSpan groupCreateSpan = new GroupCreateSpan(context, object);
                        groupCreateSpan.setOnClickListener(spanClickListener);
                        selectedContacts.put(id, groupCreateSpan);
                        spansContainer.addSpan(groupCreateSpan, true);
                    }
                }
                spansCountChanged(true);
                AndroidUtilities.updateVisibleRows(listView);
            }
        });

        listView.setItemAnimator(new ItemAnimator());
        updateRows();


        spansScrollView = new ScrollView(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                if (AndroidUtilities.isTablet() || height > width) {
                    maxSize = AndroidUtilities.dp(144);
                } else {
                    maxSize = AndroidUtilities.dp(56);
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.AT_MOST));
            }
        };
        spansScrollView.setVisibility(View.GONE);
        spansScrollView.setClipChildren(false);
        spansScrollView.addView(spansContainer);
        containerView.addView(spansScrollView);

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setImageResource(R.drawable.floating_check);

        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }

        floatingButton.setOnClickListener(v -> {
            if (dialogsDelegate == null && selectedContacts.size() == 0) {
                return;
            }
            Activity activity = AndroidUtilities.findActivity(context);
            if (activity == null) {
                return;
            }
            if (dialogsDelegate != null) {
                ArrayList<Long> dialogs = new ArrayList<>();
                for (int a = 0; a < selectedContacts.size(); a++) {
                    long uid = selectedContacts.keyAt(a);
                    dialogs.add(uid);
                }
                dialogsDelegate.didSelectDialogs(dialogs);
                dismiss();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                if (selectedContacts.size() == 1) {
                    builder.setTitle(LocaleController.getString("AddOneMemberAlertTitle", R.string.AddOneMemberAlertTitle));
                } else {
                    builder.setTitle(LocaleController.formatString("AddMembersAlertTitle", R.string.AddMembersAlertTitle, LocaleController.formatPluralString("Members", selectedContacts.size())));
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (int a = 0; a < selectedContacts.size(); a++) {
                    long uid = selectedContacts.keyAt(a);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                    if (user == null) {
                        continue;
                    }
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append(", ");
                    }
                    stringBuilder.append("**").append(ContactsController.formatName(user.first_name, user.last_name)).append("**");
                }
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
                if (selectedContacts.size() > 5) {
                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, LocaleController.formatPluralString("Members", selectedContacts.size()), chat.title)));
                    String countString = String.format("%d", selectedContacts.size());
                    int index = TextUtils.indexOf(spannableStringBuilder, countString);
                    if (index >= 0) {
                        spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), index, index + countString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    builder.setMessage(spannableStringBuilder);
                } else {
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, stringBuilder, chat.title)));
                }
                builder.setPositiveButton(LocaleController.getString("Add", R.string.Add), (dialogInterface, i) -> onAddToGroupDone(0));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.create();
                builder.show();
            }
        });
        floatingButton.setVisibility(View.INVISIBLE);
        floatingButton.setScaleX(0.0f);
        floatingButton.setScaleY(0.0f);
        floatingButton.setAlpha(0.0f);
        floatingButton.setContentDescription(LocaleController.getString("Next", R.string.Next));

        containerView.addView(floatingButton, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.RIGHT | Gravity.BOTTOM, 14, 14, 14, 14));

        ((ViewGroup.MarginLayoutParams) emptyView.getLayoutParams()).topMargin = AndroidUtilities.dp(20);
        ((ViewGroup.MarginLayoutParams) emptyView.getLayoutParams()).leftMargin = AndroidUtilities.dp(4);
        ((ViewGroup.MarginLayoutParams) emptyView.getLayoutParams()).rightMargin = AndroidUtilities.dp(4);
    }

    private void onAddToGroupDone(int i) {
        ArrayList<TLRPC.User> result = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedContacts.keyAt(a));
            result.add(user);
        }
        if (delegate != null) {
            delegate.didSelectUsers(result, i);
        }
        dismiss();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    public void setSelectedContacts(ArrayList<Long> dialogs) {
        for (int a = 0, N = dialogs.size(); a < N; a++) {
            long dialogId = dialogs.get(a);
            TLObject object;
            if (DialogObject.isChatDialog(dialogId)) {
                object = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            } else {
                object = MessagesController.getInstance(currentAccount).getUser(dialogId);
            }
            GroupCreateSpan span = new GroupCreateSpan(spansContainer.getContext(), object);
            spansContainer.addSpan(span, false);
            span.setOnClickListener(spanClickListener);
        }
        spansCountChanged(false);

        int count = spansContainer.getChildCount();

        boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;

        if (AndroidUtilities.isTablet() || isPortrait) {
            maxSize = AndroidUtilities.dp(144);
        } else {
            maxSize = AndroidUtilities.dp(56);
        }

        int width;
        if (AndroidUtilities.isTablet()) {
            width = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f);
        } else {
            width = isPortrait ? AndroidUtilities.displaySize.x : (int) Math.max(AndroidUtilities.displaySize.x * 0.8f, Math.min(AndroidUtilities.dp(480), AndroidUtilities.displaySize.x));
        }
        int maxWidth = width - AndroidUtilities.dp(26);
        int currentLineWidth = 0;
        int y = AndroidUtilities.dp(10);
        for (int a = 0; a < count; a++) {
            View child = spansContainer.getChildAt(a);
            if (!(child instanceof GroupCreateSpan)) {
                continue;
            }
            child.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), View.MeasureSpec.EXACTLY));
            if (currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                currentLineWidth = 0;
            }
            currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
        }

        int animateToH = y + AndroidUtilities.dp(32 + 10);

        int newAdditionalH;
        if (dialogsDelegate != null) {
            newAdditionalH = spanEnter ? Math.min(maxSize, animateToH) : 0;
        } else {
            newAdditionalH = Math.max(0, Math.min(maxSize, animateToH) - AndroidUtilities.dp(52));
        }
        int oldSearchAdditionalH = searchAdditionalHeight;
        searchAdditionalHeight = (selectedContacts.size() > 0 ? AndroidUtilities.dp(56) : 0);
        if (newAdditionalH != additionalHeight || oldSearchAdditionalH != searchAdditionalHeight) {
            additionalHeight = newAdditionalH;
        }
    }

    private void spansCountChanged(boolean animated) {
        boolean enter = selectedContacts.size() > 0;
        if (spanEnter != enter) {
            if (spansEnterAnimator != null) {
                spansEnterAnimator.removeAllListeners();
                spansEnterAnimator.cancel();
            }
            spanEnter = enter;
            if (spanEnter) {
                spansScrollView.setVisibility(View.VISIBLE);
            }
            if (animated) {
                spansEnterAnimator = ValueAnimator.ofFloat(spansEnterProgress, enter ? 1f : 0f);
                spansEnterAnimator.addUpdateListener(valueAnimator1 -> {
                    spansEnterProgress = (float) valueAnimator1.getAnimatedValue();
                    containerView.invalidate();
                });
                spansEnterAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        spansEnterProgress = enter ? 1f : 0f;
                        containerView.invalidate();
                        if (!enter) {
                            spansScrollView.setVisibility(View.GONE);
                        }
                    }
                });
                spansEnterAnimator.setDuration(150);
                spansEnterAnimator.start();

                if (!spanEnter && dialogsDelegate == null) {
                    if (currentDoneButtonAnimation != null) {
                        currentDoneButtonAnimation.cancel();
                    }
                    currentDoneButtonAnimation = new AnimatorSet();
                    currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 0.0f),
                            ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 0.0f),
                            ObjectAnimator.ofFloat(floatingButton, View.ALPHA, 0.0f));
                    currentDoneButtonAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            floatingButton.setVisibility(View.INVISIBLE);
                        }
                    });
                    currentDoneButtonAnimation.setDuration(180);
                    currentDoneButtonAnimation.start();
                } else {
                    if (currentDoneButtonAnimation != null) {
                        currentDoneButtonAnimation.cancel();
                    }
                    currentDoneButtonAnimation = new AnimatorSet();
                    floatingButton.setVisibility(View.VISIBLE);
                    currentDoneButtonAnimation.playTogether(ObjectAnimator.ofFloat(floatingButton, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(floatingButton, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(floatingButton, View.ALPHA, 1.0f));
                    currentDoneButtonAnimation.setDuration(180);
                    currentDoneButtonAnimation.start();
                }
            } else {
                spansEnterProgress = enter ? 1.0f : 0.0f;
                containerView.invalidate();
                if (!enter) {
                    spansScrollView.setVisibility(View.GONE);
                }
                if (currentDoneButtonAnimation != null) {
                    currentDoneButtonAnimation.cancel();
                }
                if (!spanEnter && dialogsDelegate == null) {
                    floatingButton.setScaleY(0.0f);
                    floatingButton.setScaleX(0.0f);
                    floatingButton.setAlpha(0.0f);
                    floatingButton.setVisibility(View.INVISIBLE);
                } else {
                    floatingButton.setScaleY(1.0f);
                    floatingButton.setScaleX(1.0f);
                    floatingButton.setAlpha(1.0f);
                    floatingButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void updateRows() {
        contactsStartRow = -1;
        contactsEndRow = -1;
        noContactsStubRow = -1;

        rowCount = 0;
        emptyRow = rowCount++;
        if (dialogsDelegate == null) {
            copyLinkRow = rowCount++;
            if (contacts.size() != 0) {
                contactsStartRow = rowCount;
                rowCount += contacts.size();
                contactsEndRow = rowCount;
            } else {
                noContactsStubRow = rowCount++;
            }
        } else {
            copyLinkRow = -1;
            if (dialogsServerOnly.size() != 0) {
                contactsStartRow = rowCount;
                rowCount += dialogsServerOnly.size();
                contactsEndRow = rowCount;
            } else {
                noContactsStubRow = rowCount++;
            }
        }

        lastRow = rowCount++;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (dialogsDelegate != null && dialogsServerOnly.isEmpty()) {
                dialogsServerOnly = new ArrayList<>(MessagesController.getInstance(currentAccount).dialogsServerOnly);
                listViewAdapter.notifyDataSetChanged();
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view;
            switch (viewType) {
                default:
                case 1:
                    ManageChatTextCell manageChatTextCell = new ManageChatTextCell(context);
                    manageChatTextCell.setText(LocaleController.getString("VoipGroupCopyInviteLink", R.string.VoipGroupCopyInviteLink), null, R.drawable.msg_link, 7, true);
                    manageChatTextCell.setColors(Theme.key_dialogTextBlue2, Theme.key_dialogTextBlue2);
                    view = manageChatTextCell;
                    break;
                case 2:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48) + additionalHeight, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 3:
                    view = new GroupCreateUserCell(context, 1, 0, dialogsDelegate != null);
                    break;
                case 4:
                    view = new View(context);
                    break;
                case 5:
                    StickerEmptyView stickerEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_NO_CONTACTS) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            stickerView.getImageReceiver().startAnimation();
                        }
                    };
                    stickerEmptyView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    stickerEmptyView.subtitle.setVisibility(View.GONE);
                    if (dialogsDelegate != null) {
                        stickerEmptyView.title.setText(LocaleController.getString("FilterNoChats", R.string.FilterNoChats));
                    } else {
                        stickerEmptyView.title.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
                    }
                    stickerEmptyView.setAnimateLayoutChange(true);
                    view = stickerEmptyView;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        public TLObject getObject(int position) {
            if (dialogsDelegate != null) {
                TLRPC.Dialog dialog = dialogsServerOnly.get(position - contactsStartRow);
                if (DialogObject.isUserDialog(dialog.id)) {
                    return MessagesController.getInstance(currentAccount).getUser(dialog.id);
                } else {
                    return MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                }
            } else {
                return contacts.get(position - contactsStartRow);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 2:
                    holder.itemView.requestLayout();
                    break;
                case 3:
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLObject object = getObject(position);

                    Object oldObject = cell.getObject();
                    long oldId;
                    if (oldObject instanceof TLRPC.User) {
                        oldId = ((TLRPC.User) oldObject).id;
                    } else if (oldObject instanceof TLRPC.Chat) {
                        oldId = -((TLRPC.Chat) oldObject).id;
                    } else {
                        oldId = 0;
                    }

                    cell.setObject(object, null, null, position != contactsEndRow);
                    long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = 0;
                    }
                    if (id != 0) {
                        if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                            cell.setChecked(true, false);
                            cell.setCheckBoxEnabled(false);
                        } else {
                            cell.setChecked(selectedContacts.indexOfKey(id) >= 0, oldId == id);
                            cell.setCheckBoxEnabled(true);
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == copyLinkRow) {
                return 1;
            } else if (position == emptyRow) {
                return 2;
            } else if (position >= contactsStartRow && position < contactsEndRow) {
                return 3;
            } else if (position == lastRow) {
                return 4;
            } else if (position == noContactsStubRow) {
                return 5;
            }
            return 0;
        }


        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 3 || holder.getItemViewType() == 1) {
                return true;
            }
            return false;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private ArrayList<Object> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private final SearchAdapterHelper searchAdapterHelper;
        private int currentItemsCount;
        private Runnable searchRunnable;

        public SearchAdapter() {
            searchAdapterHelper = new SearchAdapterHelper(false);
            searchAdapterHelper.setDelegate((searchId) -> {
                showItemsAnimated(currentItemsCount - 1);
                if (searchRunnable == null && !searchAdapterHelper.isSearchInProgress() && getItemCount() <= 2) {
                    emptyView.showProgress(false, true);
                }
                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1) {
                return true;
            }
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view;
            switch (viewType) {
                default:
                case 0:
                    view = new GroupCreateSectionCell(context);
                    break;
                case 1:
                    view = new GroupCreateUserCell(context, 1, 0, false);
                    break;
                case 2:
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48) + additionalHeight + searchAdditionalHeight, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 4:
                    view = new View(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 2:
                    holder.itemView.requestLayout();
                    break;
                case 0: {
                    GroupCreateSectionCell cell = (GroupCreateSectionCell) holder.itemView;
                    cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                    break;
                }
                case 1: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLObject object;
                    CharSequence username = null;
                    CharSequence name = null;

                    int localCount = searchResult.size();
                    int globalCount = searchAdapterHelper.getGlobalSearch().size();
                    int localServerCount = searchAdapterHelper.getLocalServerSearch().size();

                    position--;
                    if (position >= 0 && position < localCount) {
                        object = (TLObject) searchResult.get(position);
                    } else if (position >= localCount && position < localServerCount + localCount) {
                        object = searchAdapterHelper.getLocalServerSearch().get(position - localCount);
                    } else if (position > localCount + localServerCount && position <= globalCount + localCount + localServerCount) {
                        object = searchAdapterHelper.getGlobalSearch().get(position - localCount - localServerCount - 1);
                    } else {
                        object = null;
                    }
                    if (object != null) {
                        String objectUserName;
                        if (object instanceof TLRPC.User) {
                            objectUserName = ((TLRPC.User) object).username;
                        } else {
                            objectUserName = ((TLRPC.Chat) object).username;
                        }
                        if (position < localCount) {
                            name = searchResultNames.get(position);
                            if (name != null && !TextUtils.isEmpty(objectUserName)) {
                                if (name.toString().startsWith("@" + objectUserName)) {
                                    username = name;
                                    name = null;
                                }
                            }
                        } else if (position > localCount && !TextUtils.isEmpty(objectUserName)) {
                            String foundUserName = searchAdapterHelper.getLastFoundUsername();
                            if (foundUserName.startsWith("@")) {
                                foundUserName = foundUserName.substring(1);
                            }
                            try {
                                int index;
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                spannableStringBuilder.append("@");
                                spannableStringBuilder.append(objectUserName);
                                if ((index = AndroidUtilities.indexOfIgnoreCase(objectUserName, foundUserName)) != -1) {
                                    int len = foundUserName.length();
                                    if (index == 0) {
                                        len++;
                                    } else {
                                        index++;
                                    }
                                    spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                username = spannableStringBuilder;
                            } catch (Exception e) {
                                username = objectUserName;
                            }
                        }
                    }


                    Object oldObject = cell.getObject();
                    long oldId;
                    if (oldObject instanceof TLRPC.User) {
                        oldId = ((TLRPC.User) oldObject).id;
                    } else if (oldObject instanceof TLRPC.Chat) {
                        oldId = -((TLRPC.Chat) oldObject).id;
                    } else {
                        oldId = 0;
                    }

                    cell.setObject(object, name, username);
                    long id;
                    if (object instanceof TLRPC.User) {
                        id = ((TLRPC.User) object).id;
                    } else if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = 0;
                    }
                    if (id != 0) {
                        if (ignoreUsers != null && ignoreUsers.indexOfKey(id) >= 0) {
                            cell.setChecked(true, oldId == id);
                            cell.setCheckBoxEnabled(false);
                        } else {
                            cell.setChecked(selectedContacts.indexOfKey(id) >= 0, oldId == id);
                            cell.setCheckBoxEnabled(true);
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 2;
            } else if (position == currentItemsCount - 1) {
                return 4;
            }
            position--;
            if (position == searchResult.size() + searchAdapterHelper.getLocalServerSearch().size()) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getItemCount() {
            int count = searchResult.size();
            int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
            int globalCount = searchAdapterHelper.getGlobalSearch().size();
            count += localServerCount;
            if (globalCount != 0) {
                count += globalCount + 1;
            }
            // if (count > 0) {
            count += 2;
            //  }
            currentItemsCount = count;
            return count;
        }

        private void updateSearchResults(final ArrayList<Object> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;
                searchResult = users;
                searchResultNames = names;
                searchAdapterHelper.mergeResults(searchResult);
                showItemsAnimated(currentItemsCount - 1);
                notifyDataSetChanged();
                if (!searchAdapterHelper.isSearchInProgress() && getItemCount() <= 2) {
                    emptyView.showProgress(false, true);
                }
            });
        }

        public void searchDialogs(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }

            searchResult.clear();
            searchResultNames.clear();
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, false, false, false, false, 0, false, 0, 0);
            notifyDataSetChanged();

            if (!TextUtils.isEmpty(query)) {
                if (listView.getAdapter() != searchListViewAdapter) {
                    listView.setAdapter(searchListViewAdapter);
                }
                emptyView.showProgress(true, false);
                Utilities.searchQueue.postRunnable(searchRunnable = () -> AndroidUtilities.runOnUIThread(() -> {
                    searchAdapterHelper.queryServerSearch(query, true, dialogsDelegate != null, true, dialogsDelegate != null, false, 0, false, 0, 0);
                    Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<Object> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contacts.size(); a++) {
                            TLObject object = contacts.get(a);

                            String name;
                            String username;

                            if (object instanceof TLRPC.User) {
                                TLRPC.User user = (TLRPC.User) object;
                                name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                username = user.username;
                            } else {
                                TLRPC.Chat chat = (TLRPC.Chat) object;
                                name = chat.title;
                                username = chat.username;
                            }
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    if (found == 1) {
                                        if (object instanceof TLRPC.User) {
                                            TLRPC.User user = (TLRPC.User) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else {
                                            TLRPC.Chat chat = (TLRPC.Chat) object;
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(chat.title, null, q));
                                        }
                                    } else {
                                        resultArrayNames.add(AndroidUtilities.generateSearchName("@" + username, null, "@" + q));
                                    }
                                    resultArray.add(object);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArray, resultArrayNames);
                    });
                }), 300);
            } else {
                if (listView.getAdapter() != listViewAdapter) {
                    listView.setAdapter(listViewAdapter);
                }
            }
        }
    }

    boolean enterEventSent;
    float y;

    @Override
    protected void onSearchViewTouched(MotionEvent ev, EditTextBoldCursor searchEditText) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            y = scrollOffsetY;
        } else if (ev.getAction() == MotionEvent.ACTION_UP && Math.abs(scrollOffsetY - y) < touchSlop) {
            if (!enterEventSent) {
                Activity activity = AndroidUtilities.findActivity(getContext());
                BaseFragment fragment = null;
                if (activity instanceof LaunchActivity) {
                    fragment = ((LaunchActivity) activity).getActionBarLayout().fragmentsStack.get(((LaunchActivity) activity).getActionBarLayout().fragmentsStack.size() - 1);
                }
                if (fragment instanceof ChatActivity) {
                    boolean keyboardVisible = ((ChatActivity) fragment).needEnterText();
                    enterEventSent = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        setFocusable(true);
                        searchEditText.requestFocus();
                        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(searchEditText));
                    }, keyboardVisible ? 200 : 0);
                } else {
                    enterEventSent = true;
                    setFocusable(true);
                    searchEditText.requestFocus();
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(searchEditText));
                }
            }
        }
    }

    private class SpansContainer extends ViewGroup {

        private boolean animationStarted;
        private ArrayList<Animator> animators = new ArrayList<>();
        private View removingSpan;
        private int animationIndex = -1;
        boolean addAnimation;

        public SpansContainer(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int count = getChildCount();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int maxWidth = width - AndroidUtilities.dp(26);
            int currentLineWidth = 0;
            int y = AndroidUtilities.dp(10);
            int allCurrentLineWidth = 0;
            int allY = AndroidUtilities.dp(10);
            int x;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof GroupCreateSpan)) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
                if (child != removingSpan && currentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    y += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    currentLineWidth = 0;
                }
                if (allCurrentLineWidth + child.getMeasuredWidth() > maxWidth) {
                    allY += child.getMeasuredHeight() + AndroidUtilities.dp(8);
                    allCurrentLineWidth = 0;
                }
                x = AndroidUtilities.dp(13) + currentLineWidth;
                if (!animationStarted) {
                    if (child == removingSpan) {
                        child.setTranslationX(AndroidUtilities.dp(13) + allCurrentLineWidth);
                        child.setTranslationY(allY);
                    } else if (removingSpan != null) {
                        if (child.getTranslationX() != x) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_X, x));
                        }
                        if (child.getTranslationY() != y) {
                            animators.add(ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, y));
                        }
                    } else {
                        child.setTranslationX(x);
                        child.setTranslationY(y);
                    }
                }
                if (child != removingSpan) {
                    currentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
                }
                allCurrentLineWidth += child.getMeasuredWidth() + AndroidUtilities.dp(9);
            }

            int h = allY + AndroidUtilities.dp(32 + 10);
            int animateToH = y + AndroidUtilities.dp(32 + 10);

            int newAdditionalH;
            if (dialogsDelegate != null) {
                newAdditionalH = spanEnter ? Math.min(maxSize, animateToH) : 0;
            } else {
                newAdditionalH = Math.max(0, Math.min(maxSize, animateToH) - AndroidUtilities.dp(52));
            }
            int oldSearchAdditionalH = searchAdditionalHeight;
            searchAdditionalHeight = (dialogsDelegate == null && selectedContacts.size() > 0 ? AndroidUtilities.dp(56) : 0);
            if (newAdditionalH != additionalHeight || oldSearchAdditionalH != searchAdditionalHeight) {
                additionalHeight = newAdditionalH;
                if (listView.getAdapter() != null && listView.getAdapter().getItemCount() > 0) {
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
                    if (holder != null) {
                        listView.getAdapter().notifyItemChanged(0);
                        layoutManager.scrollToPositionWithOffset(0, holder.itemView.getTop() - listView.getPaddingTop());
                    }
                }
            }

            int newSize = Math.min(maxSize, animateToH);
            if (scrollViewH != newSize) {
                ValueAnimator valueAnimator = ValueAnimator.ofInt(scrollViewH, newSize);
                valueAnimator.addUpdateListener(valueAnimator1 -> {
                    scrollViewH = (int) valueAnimator1.getAnimatedValue();
                    containerView.invalidate();
                });
                animators.add(valueAnimator);
            }

            if (addAnimation && animateToH > maxSize) {
                AndroidUtilities.runOnUIThread(() -> spansScrollView.smoothScrollTo(0, animateToH - maxSize));
            } else if (!addAnimation && spansScrollView.getScrollY() + spansScrollView.getMeasuredHeight() > animateToH) {
                AndroidUtilities.runOnUIThread(() -> spansScrollView.smoothScrollTo(0, animateToH - maxSize));
            }

            if (!animationStarted) {
                if (currentAnimation != null) {
                    currentAnimation.playTogether(animators);
                    currentAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentAnimation = null;
                            requestLayout();
                        }
                    });

                    currentAnimation.start();
                    animationStarted = true;
                }
            }

            if (currentAnimation == null) {
                scrollViewH = newSize;
                containerView.invalidate();
            }

            setMeasuredDimension(width, Math.max(animateToH, h));

            listView.setTranslationY(0);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }

        public void addSpan(final GroupCreateSpan span, boolean animated) {
            addAnimation = true;
            selectedContacts.put(span.getUid(), span);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            if (animated) {
                currentAnimation = new AnimatorSet();
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        currentAnimation = null;
                        animationStarted = false;
                    }
                });
                currentAnimation.setDuration(150);
                currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animators.clear();
                animators.add(ObjectAnimator.ofFloat(span, View.SCALE_X, 0.01f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(span, View.SCALE_Y, 0.01f, 1.0f));
                animators.add(ObjectAnimator.ofFloat(span, View.ALPHA, 0.0f, 1.0f));
            }
            addView(span);
        }

        public void removeSpan(final GroupCreateSpan span) {
            addAnimation = false;
            boolean ignoreScrollEvent = true;
            selectedContacts.remove(span.getUid());
            span.setOnClickListener(null);

            if (currentAnimation != null) {
                currentAnimation.setupEndValues();
                currentAnimation.cancel();
            }
            animationStarted = false;
            currentAnimation = new AnimatorSet();
            currentAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    removeView(span);
                    removingSpan = null;
                    currentAnimation = null;
                    animationStarted = false;
                }
            });
            currentAnimation.setDuration(150);
            removingSpan = span;
            animators.clear();
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_X, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.SCALE_Y, 1.0f, 0.01f));
            animators.add(ObjectAnimator.ofFloat(removingSpan, View.ALPHA, 1.0f, 0.0f));
            requestLayout();
        }
    }

    @Override
    protected ContainerView createContainerView(Context context) {
        return new ContainerView(context) {

            float emptyViewOffset;
            float animateToEmptyViewOffset;

            Paint paint = new Paint();

            float deltaOffset;

            private VerticalPositionAutoAnimator verticalPositionAutoAnimator;

            @Override
            public void onViewAdded(View child) {
                if (child == floatingButton && verticalPositionAutoAnimator == null) {
                    verticalPositionAutoAnimator = VerticalPositionAutoAnimator.attach(child);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (verticalPositionAutoAnimator != null) {
                    verticalPositionAutoAnimator.ignoreNextLayout();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                spansScrollView.setTranslationY(y + AndroidUtilities.dp(64));

                float newEmptyViewOffset = additionalHeight + searchAdditionalHeight;
                if (emptyView.getVisibility() != View.VISIBLE) {
                    emptyViewOffset = newEmptyViewOffset;
                    animateToEmptyViewOffset = emptyViewOffset;
                } else {
                   if (animateToEmptyViewOffset != newEmptyViewOffset) {
                       animateToEmptyViewOffset = newEmptyViewOffset;
                       deltaOffset = (newEmptyViewOffset - emptyViewOffset)  * (16f / 150f);
                   }
                }

                if (emptyViewOffset != animateToEmptyViewOffset) {
                    emptyViewOffset += deltaOffset;
                    if (deltaOffset > 0 && emptyViewOffset > animateToEmptyViewOffset) {
                        emptyViewOffset = animateToEmptyViewOffset;
                    } else if (deltaOffset < 0 && emptyViewOffset < animateToEmptyViewOffset) {
                        emptyViewOffset = animateToEmptyViewOffset;
                    } else {
                        invalidate();
                    }
                }
                emptyView.setTranslationY(scrollOffsetY + emptyViewOffset);
                super.dispatchDraw(canvas);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == spansScrollView) {
                    canvas.save();
                    canvas.clipRect(0, child.getY() - AndroidUtilities.dp(4), getMeasuredWidth(), child.getY() + scrollViewH + 1);
                    canvas.drawColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), (int) (255 * spansEnterProgress)));
                    paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), (int) (255 * spansEnterProgress)));
                    canvas.drawRect(0, child.getY() + scrollViewH, getMeasuredWidth(), child.getY() + scrollViewH + 1, paint);
                    boolean rez = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return rez;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
    }


    @Override
    protected void search(String text) {
        searchAdapter.searchDialogs(text);
    }

    public void setDelegate(GroupCreateActivity.ContactsAddActivityDelegate contactsAddActivityDelegate) {
        delegate = contactsAddActivityDelegate;
    }

    public void setDelegate(InviteMembersBottomSheetDelegate inviteMembersBottomSheetDelegate, ArrayList<Long> selectedDialogs) {
        dialogsDelegate = inviteMembersBottomSheetDelegate;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        dialogsServerOnly = new ArrayList<>(MessagesController.getInstance(currentAccount).dialogsServerOnly);
        updateRows();
    }

    private class ItemAnimator extends DefaultItemAnimator {

        public ItemAnimator() {
            translationInterpolator = CubicBezierInterpolator.DEFAULT;
            setMoveDuration(150);
            setAddDuration(150);
            setRemoveDuration(150);
            setShowWithoutAnimation(false);
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (enterEventSent) {
            Activity activity = AndroidUtilities.findActivity(getContext());
            if (activity instanceof LaunchActivity) {
                BaseFragment fragment = ((LaunchActivity) activity).getActionBarLayout().fragmentsStack.get(((LaunchActivity) activity).getActionBarLayout().fragmentsStack.size() - 1);
                if (fragment instanceof ChatActivity) {
                    ((ChatActivity) fragment).onEditTextDialogClose(true);
                }
            }
        }
    }

    boolean linkGenerating;
    TLRPC.TL_chatInviteExported invite;

    private void generateLink() {
        if (linkGenerating) {
            return;
        }
        linkGenerating = true;
        TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
        req.legacy_revoke_permanent = true;
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                invite = (TLRPC.TL_chatInviteExported) response;

                TLRPC.ChatFull chatInfo = MessagesController.getInstance(currentAccount).getChatFull(chatId);
                if (chatInfo != null) {
                    chatInfo.exported_invite = invite;
                }

                if (invite.link == null) {
                    return;
                }
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", invite.link);
                clipboard.setPrimaryClip(clip);
                BulletinFactory.createCopyLinkBulletin(parentFragment).show();
                dismiss();

            }
            linkGenerating = false;
        }));
    }
}

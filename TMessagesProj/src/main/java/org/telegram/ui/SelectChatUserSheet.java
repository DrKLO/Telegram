package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class SelectChatUserSheet extends BottomSheetWithRecyclerListView {

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AppointNewOwner);
    }

    private TLRPC.Chat chat;
    private final TLObject initialOwner;
    private final Runnable whenTransferred;

    private TLObject selectedOwner;

    private FrameLayout searchContainer;
    private FrameLayout searchBox;
    private EditTextBoldCursor searchEdit;
    private FrameLayout emptySearchView;

    private ParticipantsList admins;
    private ParticipantsList members;
    private ParticipantsList search;

    private View bottomGradient;
    private ButtonWithCounterView button;

    public SelectChatUserSheet(
        Context context,
        TLRPC.Chat chat,
        TLRPC.User futureCreator,
        Runnable whenTransferred,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, null, true, false, false, ActionBarType.SLIDING, resourcesProvider);

        headerMoveTop = dp(12);
        smoothKeyboardAnimationEnabled = true;
//        smoothKeyboardByBottom = true;

        this.chat = chat;
        this.initialOwner = futureCreator;
        this.selectedOwner = futureCreator;
        this.whenTransferred = whenTransferred;
        final TLRPC.User self = UserConfig.getInstance(currentAccount).getCurrentUser();

        searchContainer = new FrameLayout(context);
        searchBox = new FrameLayout(context);
        searchBox.setBackground(Theme.createRoundRectDrawable(dp(20), getThemedColor(Theme.key_dialogSearchBackground)));
        final ImageView searchImageView = new ImageView(context);
        searchImageView.setScaleType(ImageView.ScaleType.CENTER);
        searchImageView.setImageResource(R.drawable.smiles_inputsearch);
        searchImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogSearchHint), PorterDuff.Mode.SRC_IN));
        searchBox.addView(searchImageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        searchEdit = new EditTextBoldCursor(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        searchEdit.setTextColor(getThemedColor(Theme.key_dialogSearchText));
        searchEdit.setHintTextColor(getThemedColor(Theme.key_dialogSearchHint));
        searchEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchEdit.setSingleLine(true);
        searchEdit.setBackground(null);
        searchEdit.setCursorWidth(1.5f);
        searchEdit.setGravity(Gravity.FILL_VERTICAL);
        searchEdit.setClipToPadding(true);
        searchEdit.setPadding(dp(46), 0, dp(16), 0);
        searchEdit.setTranslationY(-dp(.66f));
        searchEdit.setInputType(searchEdit.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchEdit.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchEdit.setTextIsSelectable(false);
        searchEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                AndroidUtilities.hideKeyboard(searchEdit);
            }
            return false;
        });
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() <= 0) {
                    AndroidUtilities.cancelRunOnUIThread(updateSearchRunnable);
                    updateSearch();
                } else {
                    AndroidUtilities.cancelRunOnUIThread(updateSearchRunnable);
                    AndroidUtilities.runOnUIThread(updateSearchRunnable, 300);
                }
            }
            private final Runnable updateSearchRunnable = () -> this.updateSearch();
            private void updateSearch() {
                final TLRPC.TL_channelParticipantsSearch filter = new TLRPC.TL_channelParticipantsSearch();
                filter.q = searchEdit.getText().toString();
                search.setFilter(filter);
                update();
            }
        });
        searchEdit.setHint(getString(R.string.SearchMembers));
        searchBox.addView(searchEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        searchContainer.addView(searchBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.FILL_HORIZONTAL | Gravity.CENTER_VERTICAL, 11, 0, 11, 0));
        containerView.addView(searchContainer, Math.max(0, containerView.indexOfChild(actionBar)), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.TOP | Gravity.FILL_HORIZONTAL, (backgroundPaddingLeft / AndroidUtilities.density), 0, (backgroundPaddingLeft / AndroidUtilities.density), 0));

        emptySearchView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(240), MeasureSpec.EXACTLY)
                );
            }
        };
        final BackupImageView emptyImageView = new BackupImageView(context);
        emptyImageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(130), dp(130)));
        emptySearchView.addView(emptyImageView, LayoutHelper.createFrame(130, 130, Gravity.CENTER));

        admins = new ParticipantsList(currentAccount, chat.id, new TLRPC.TL_channelParticipantsAdmins()).listen(this::update);
        members = new ParticipantsList(currentAccount, chat.id, new TLRPC.TL_channelParticipantsRecent()).listen(this::update);
        search = new ParticipantsList(currentAccount, chat.id, new TLRPC.TL_channelParticipantsSearch()).listen(this::update);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(48 + 10 + 10));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setOnItemClickListener((view, position) -> {
            position--;
            final UItem item = adapter.getItem(position);
            if (item == null) return;
            if (item.object instanceof TLRPC.User || item.object instanceof TLRPC.Chat) {
                ((ProfileSearchCell) view).setChecked(true, true);
                selectedOwner = (TLObject) item.object;
                updateButton(true);
                adapter.update(true);
            }
        });
        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                updateSearchY();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateSearchY();
            }
        });
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerListView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(searchEdit);
                }
                updateSearchY();
            }
        });

        bottomGradient = new View(getContext()) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final LinearGradient gradient = new LinearGradient(0, 0, 0, dp(10 + 48 + 10), new int[] {
                Theme.multAlpha(getThemedColor(Theme.key_dialogBackground), 0.0f),
                getThemedColor(Theme.key_dialogBackground)
            }, new float[] {0, 0.2f}, Shader.TileMode.CLAMP);
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                paint.setShader(gradient);
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
        };
        containerView.addView(bottomGradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 10 + 48 + 10, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        button = new ButtonWithCounterView(getContext(), resourcesProvider).setRound();
        button.setColor(getThemedColor(Theme.key_fill_RedNormal));
        updateButton(false);
        containerView.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 10 + (backgroundPaddingLeft / AndroidUtilities.density), 10, 10 + (backgroundPaddingLeft / AndroidUtilities.density), 10));
        button.setOnClickListener(v -> {
            if (!(selectedOwner instanceof TLRPC.User)) return;
            if (button.isLoading()) return;
            button.setLoading(true);

            initTransfer((TLRPC.User) selectedOwner, null, null);
        });

        if (adapter != null) {
            adapter.update(false);
        }
        admins.load();
        members.load();
    }

    private void updateButton(boolean animated) {
        final int stringId = ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.LeaveChannelAndAppoint : R.string.LeaveGroupAndAppoint;
        final float widthForName = Math.max(dp(32), (button.getWidth() > 0 ? button.getWidth() : AndroidUtilities.displaySize.x - dp(10 + 10)) - dp(16) - button.getTextPaint().measureText(getString(stringId)));
        button.setText(formatString(stringId, TextUtils.ellipsize(DialogObject.getShortTitle(selectedOwner), button.getTextPaint(), widthForName, TextUtils.TruncateAt.MIDDLE)), animated);
    }

    private void updateSearchY() {
        float searchTop = -dp(64);
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            final View child = recyclerListView.getChildAt(i);
            if (child.getId() == ID_SEARCH) {
                searchTop = child.getY();
                break;
            }
        }

        searchContainer.setTranslationY(searchTop);
    }

    private void update() {
        if (adapter != null) {
            adapter.update(true);
        }
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
    }

    private boolean isInitialOwnerAdmin() {
        for (TLObject obj : admins.users) {
            if (DialogObject.getDialogId(obj) == DialogObject.getDialogId(initialOwner))
                return true;
        }
        return false;
    }

    private final static int ID_SEARCH = 3;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (admins == null || members == null) return;
        final HashSet<Long> dialogIds = new HashSet<>();
        dialogIds.add(UserConfig.getInstance(currentAccount).getClientUserId());
        items.add(UItem.asSpace(ID_SEARCH, dp(64)));
        boolean first = true;
        if (search != null && !TextUtils.isEmpty(search.filter.q)) {
            for (TLObject obj : search.users) {
                if (dialogIds.contains(DialogObject.getDialogId(obj))) continue;
                dialogIds.add(DialogObject.getDialogId(obj));
                items.add(UItem.asProfileCell(obj).setChecked(DialogObject.getDialogId(obj) == DialogObject.getDialogId(selectedOwner)));
            }
            if (search.loading) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            if (items.size() == 1) {
                items.add(UItem.asCustom(emptySearchView));
            }
        } else {
            first = true;
            if (initialOwner != null && isInitialOwnerAdmin() && !dialogIds.contains(DialogObject.getDialogId(initialOwner))) {
                if (first) {
                    items.add(UItem.asGraySection(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelAdmins : R.string.GroupAdmins)));
                    first = false;
                }
                dialogIds.add(DialogObject.getDialogId(initialOwner));
                items.add(UItem.asProfileCell(initialOwner).setChecked(DialogObject.getDialogId(initialOwner) == DialogObject.getDialogId(selectedOwner)));
            }
            for (TLObject obj : admins.users) {
                if (dialogIds.contains(DialogObject.getDialogId(obj))) continue;
                if (first) {
                    items.add(UItem.asGraySection(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelAdmins : R.string.GroupAdmins)));
                    first = false;
                }
                dialogIds.add(DialogObject.getDialogId(obj));
                items.add(UItem.asProfileCell(obj).setChecked(DialogObject.getDialogId(obj) == DialogObject.getDialogId(selectedOwner)));
            }
            if (admins.loading) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            first = true;
            if (initialOwner != null && !dialogIds.contains(DialogObject.getDialogId(initialOwner))) {
                if (first) {
                    items.add(UItem.asGraySection(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelSubscribers2 : R.string.GroupMembers2)));
                    first = false;
                }
                dialogIds.add(DialogObject.getDialogId(initialOwner));
                items.add(UItem.asProfileCell(initialOwner).setChecked(DialogObject.getDialogId(initialOwner) == DialogObject.getDialogId(selectedOwner)));
            }
            for (TLObject obj : members.users) {
                if (dialogIds.contains(DialogObject.getDialogId(obj))) continue;
                if (first) {
                    items.add(UItem.asGraySection(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.ChannelSubscribers2 : R.string.GroupMembers2)));
                    first = false;
                }
                dialogIds.add(DialogObject.getDialogId(obj));
                items.add(UItem.asProfileCell(obj).setChecked(DialogObject.getDialogId(obj) == DialogObject.getDialogId(selectedOwner)));
            }
            if (!members.users.isEmpty() && members.loading) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        admins.detach();
        members.detach();
        search.detach();
    }

    private static class ParticipantsList implements NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final TLRPC.Chat chat;
        private TLRPC.ChatFull chatInfo;
        public TLRPC.ChannelParticipantsFilter filter;

        public final ArrayList<TLObject> users = new ArrayList<>();

        public ParticipantsList(int currentAccount, long chatId, TLRPC.ChannelParticipantsFilter filter) {
            this.currentAccount = currentAccount;
            this.chat = MessagesController.getInstance(currentAccount).getChat(chatId);
            this.chatInfo = MessagesController.getInstance(currentAccount).getChatFull(chatId);
            this.filter = filter;
            if (chatInfo == null) {
                attach();
                MessagesController.getInstance(currentAccount).loadFullChat(chatId, 0, false);
            }
        }

        public ParticipantsList setFilter(TLRPC.ChannelParticipantsFilter filter) {
            final boolean changed;
            final boolean changedType;
            if (this.filter instanceof TLRPC.TL_channelParticipantsSearch && filter instanceof TLRPC.TL_channelParticipantsSearch) {
                changed = !TextUtils.equals(this.filter.q, filter.q);
                changedType = false;
            } else {
                changed = true;
                changedType = true;
            }
            this.filter = filter;
            if (changed) {
                if (changedType) {
                    clear();
                } else {
                    clearOnLoad = true;
                    endReached = false;
                }
                load();
            }
            return this;
        }

        private ArrayList<Runnable> listeners = new ArrayList<>();
        public ParticipantsList listen(Runnable onUpdate) {
            listeners.add(onUpdate);
            return this;
        }
        private void emit() {
            for (Runnable runnable : listeners)
                runnable.run();
        }

        public boolean loading;
        public boolean endReached;
        private int requestId = -1;
        private boolean clearOnLoad;

        public void clear() {
            clearOnLoad = false;
            cancel();
            users.clear();
            endReached = false;
        }
        public void load() {
            if (loading || endReached) return;
            if (filter instanceof TLRPC.TL_channelParticipantsSearch && TextUtils.isEmpty(filter.q))
                return;
            loading = true;

            if (!ChatObject.isChannel(chat)) {

            } else {
                final TLRPC.TL_channels_getParticipants request = new TLRPC.TL_channels_getParticipants();
                request.channel = MessagesController.getInputChannel(chat);
                request.filter = filter;
                request.limit = 30;
                request.offset = clearOnLoad ? 0 : users.size();
                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(request, AndroidUtilities::runOnUIThread, (res, err) -> {
                    if (err != null) {
                        if (clearOnLoad) {
                            users.clear();
                            clearOnLoad = false;
                        }
                        endReached = true;
                        loading = false;
                        emit();
                        return;
                    }

                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    if (clearOnLoad) {
                        users.clear();
                        clearOnLoad = false;
                    }
                    for (final TLRPC.ChannelParticipant participant : res.participants) {
                        final long dialogId = DialogObject.getPeerDialogId(participant.peer);
                        final TLObject obj = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
                        if (obj != null) {
                            users.add(obj);
                        }
                    }

                    if (res.participants.size() < 30) {
                        endReached = true;
                    }
                    loading = false;
                    emit();
                });
            }
        }
        public void cancel() {
            if (requestId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true);
                requestId = -1;
            }
            loading = false;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.chatInfoDidLoad) {
                final TLRPC.ChatFull info = (TLRPC.ChatFull) args[0];
                if (info.id == chat.id) {
                    this.chatInfo = info;
                    if (!ChatObject.isChannel(chat) && loading) {
                        loading = false;
                        load();
                    }
                }
            }
        }

        private boolean attached;
        public void attach() {
            if (attached) return;
            attached = true;
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        }
        public void detach() {
            if (attached) return;
            attached = false;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);

            cancel();
        }
    }

    private Context context;
    private void initTransfer(TLRPC.User user, TLRPC.InputCheckPasswordSRP srp, TwoStepVerificationActivity passwordFragment) {
        if (getContext() != null) {
            context = getContext();
        }
        if (context == null) {
            return;
        }
        if (srp != null && !ChatObject.isChannel(chat)) {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            MessagesController.getInstance(currentAccount).convertToMegaGroup(context, chat.id, lastFragment, param -> {
                if (param != 0) {
                    chat = MessagesController.getInstance(currentAccount).getChat(param);
                    initTransfer(user, srp, passwordFragment);
                }
            });
            return;
        }
        final TLRPC.TL_channels_editCreator req = new TLRPC.TL_channels_editCreator();
        if (ChatObject.isChannel(chat)) {
            req.channel = new TLRPC.TL_inputChannel();
            req.channel.channel_id = chat.id;
            req.channel.access_hash = chat.access_hash;
        } else {
            req.channel = new TLRPC.TL_inputChannelEmpty();
        }
        req.password = srp != null ? srp : new TLRPC.TL_inputCheckPasswordEmpty();
        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if (context == null) {
                    return;
                }
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    if (srp == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.EditAdminChannelTransfer : R.string.EditAdminGroupTransfer));
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.EditAdminTransferReadyAlertText2, chat.title, UserObject.getFirstName(user))));
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferChangeOwner), (dialogInterface, i) -> {
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            dismiss();

                            final TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                            fragment.setDelegate(0, password -> initTransfer(user, password, fragment));
                            lastFragment.presentFragment(fragment);
                        });
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (di, w) -> {
                            button.setLoading(false);
                        });
                        builder.show();
                    }
                } else if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(LocaleController.getString(R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(context);
                    linearLayout.setPadding(dp(24), dp(2), dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(context);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.EditChannelAdminTransferAlertText, UserObject.getFirstName(user))));
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.EditAdminTransferAlertText, UserObject.getFirstName(user))));
                    }
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(context);
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(context);
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? dp(11) : 0, dp(9), LocaleController.isRTL ? 0 : dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(context);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(context);
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(context);
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? dp(11) : 0, dp(9), LocaleController.isRTL ? 0 : dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(context);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> {
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            dismiss();

                            lastFragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null));
                        });
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (di, w) -> {
                            button.setLoading(false);
                        });
                    } else {
                        messageTextView = new TextView(context);
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString(R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString(R.string.OK), (di, w) -> {
                            button.setLoading(false);
                        });
                    }
                    builder.show();
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initTransfer(user, passwordFragment.getNewSrpPassword(), passwordFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else if (error.text.equals("CHANNELS_TOO_MUCH")) {
                    if (context != null && !AccountInstance.getInstance(currentAccount).getUserConfig().isPremium()) {
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        showDialog(new LimitReachedBottomSheet(lastFragment, context, LimitReachedBottomSheet.TYPE_TO0_MANY_COMMUNITIES, currentAccount, null));
                    } else {
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        dismiss();

                        lastFragment.presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_EDIT));
                    }
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment == null) return;
                    AlertsCreator.showAddUserAlert(error.text, lastFragment, ChatObject.isChannelAndNotMegaGroup(chat), req);
                }
            } else {
                if (srp != null) {
                    if (whenTransferred != null) {
                        whenTransferred.run();
                    }
                    dismiss();
                    passwordFragment.needHideProgress();
                    passwordFragment.finishFragment();
                }
            }
        }));
    }
}

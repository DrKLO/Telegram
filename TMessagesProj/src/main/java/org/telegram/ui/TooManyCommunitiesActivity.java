package org.telegram.ui;

import org.telegram.messenger.FileLog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TooManyCommunitiesHintCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TooManyCommunitiesActivity extends BaseFragment {

    public static final int TYPE_JOIN = 0;
    public static final int TYPE_EDIT = 1;
    public static final int TYPE_CREATE = 2;

    private RecyclerListView listView;
    private RecyclerListView searchListView;
    private TextView buttonTextView;
    private Adapter adapter;
    private SearchAdapter searchAdapter;
    private ArrayList<TLRPC.Chat> inactiveChats = new ArrayList<>();
    private ArrayList<String> inactiveChatsSignatures = new ArrayList<>();
    private FrameLayout buttonLayout;
    private EmptyTextProgressView emptyView;
    private FrameLayout searchViewContainer;

    private int buttonAnimation;

    private Set<Long> selectedIds = new HashSet<>();

    private TooManyCommunitiesHintCell hintCell;

    private ValueAnimator enterAnimator;
    private float enterProgress;
    protected RadialProgressView progressBar;

    int type;

    private int buttonHeight = AndroidUtilities.dp(64);

    Runnable showProgressRunnable = new Runnable() {
        @Override
        public void run() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setAlpha(0);
            progressBar.animate().alpha(1f).start();
        }
    };

    RecyclerListView.OnItemClickListener onItemClickListener = (view, position) -> {
        if (view instanceof GroupCreateUserCell) {
            TLRPC.Chat chat = (TLRPC.Chat) ((GroupCreateUserCell) view).getObject();
            if (selectedIds.contains(chat.id)) {
                selectedIds.remove(chat.id);
                ((GroupCreateUserCell) view).setChecked(false, true);
            } else {
                selectedIds.add(chat.id);
                ((GroupCreateUserCell) view).setChecked(true, true);
            }
            onSelectedCountChange();
            if (!selectedIds.isEmpty()) {
                RecyclerListView list = searchViewContainer.getVisibility() == View.VISIBLE ? searchListView : listView;
                int bottom = list.getHeight() - view.getBottom();
                if (bottom < buttonHeight) {
                    list.smoothScrollBy(0, buttonHeight - bottom);
                }
            }
        }
    };

    RecyclerListView.OnItemLongClickListener onItemLongClickListener = (view, position) -> {
        onItemClickListener.onItemClick(view, position);
        return true;
    };

    public TooManyCommunitiesActivity(int type) {
        super();
        Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        arguments = bundle;
    }

    @Override
    public View createView(Context context) {
        type = arguments.getInt("type", TYPE_JOIN);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.LimitReached));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

            boolean expanded = false;


            @Override
            public void onSearchCollapse() {
                super.onSearchCollapse();
                if (listView.getVisibility() != View.VISIBLE) {
                    listView.setVisibility(View.VISIBLE);
                    listView.setAlpha(0);
                }
                emptyView.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                listView.animate().alpha(1f).setDuration(150).setListener(null).start();

                searchViewContainer.animate().alpha(0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        searchViewContainer.setVisibility(View.GONE);
                    }
                }).start();
                expanded = false;
            }

            @Override
            public void onTextChanged(EditText editText) {
                String query = editText.getText().toString();
                searchAdapter.search(query);
                if (!expanded && !TextUtils.isEmpty(query)) {
                    if (searchViewContainer.getVisibility() != View.VISIBLE) {
                        searchViewContainer.setVisibility(View.VISIBLE);
                        searchViewContainer.setAlpha(0);
                    }
                    listView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            listView.setVisibility(View.GONE);
                        }
                    }).start();
                    searchAdapter.searchResultsSignatures.clear();
                    searchAdapter.searchResults.clear();
                    searchAdapter.notifyDataSetChanged();
                    searchViewContainer.animate().setListener(null).alpha(1f).setDuration(150).start();
                    expanded = true;
                } else if (expanded && TextUtils.isEmpty(query)) {
                    onSearchCollapse();
                }
            }
        });
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        FrameLayout contentView = new FrameLayout(context);
        fragmentView = contentView;
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setClipToPadding(false);
        listView.setOnItemClickListener(onItemClickListener);
        listView.setOnItemLongClickListener(onItemLongClickListener);

        searchListView = new RecyclerListView(context);
        searchListView.setLayoutManager(new LinearLayoutManager(context));
        searchListView.setAdapter(searchAdapter = new SearchAdapter());
        searchListView.setOnItemClickListener(onItemClickListener);
        searchListView.setOnItemLongClickListener(onItemLongClickListener);
        searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });
        emptyView = new EmptyTextProgressView(context);
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString(R.string.NoResult));
        emptyView.showTextView();


        progressBar = new RadialProgressView(context);
        contentView.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        adapter.updateRows();
        progressBar.setVisibility(View.GONE);

        contentView.addView(listView);
        searchViewContainer = new FrameLayout(context);
        searchViewContainer.addView(searchListView);
        searchViewContainer.addView(emptyView);
        searchViewContainer.setVisibility(View.GONE);
        contentView.addView(searchViewContainer);

        loadInactiveChannels();

        ThemeBind.backgroundColor(fragmentView, Theme.key_windowBackgroundWhite);

        buttonLayout = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawRect(0, 0, getMeasuredWidth(), 1, Theme.dividerPaint);
            }
        };
        buttonLayout.setWillNotDraw(false);
        buttonTextView = new TextView(context);
        ThemeBind.textColor(buttonTextView, Theme.key_featuredStickers_buttonText);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
        contentView.addView(buttonLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64, Gravity.BOTTOM));
        ThemeBind.backgroundColor(buttonLayout, Theme.key_windowBackgroundWhite);
        buttonLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 12, 16, 12));
        buttonLayout.setVisibility(View.GONE);
        buttonTextView.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) {
                return;
            }
            TLRPC.User currentUser = getMessagesController().getUser(getUserConfig().getClientUserId());
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            for (int i = 0; i < inactiveChats.size(); i++) {
                if (selectedIds.contains(inactiveChats.get(i).id)) {
                    chats.add(inactiveChats.get(i));
                }
            }
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                getMessagesController().putChat(chat, false);
                getMessagesController().deleteParticipantFromChat(chat.id, currentUser);
            }
            finishFragment();
        });
        return fragmentView;
    }

    private void onSelectedCountChange() {
        if (selectedIds.isEmpty() && buttonAnimation != -1 && buttonLayout.getVisibility() == View.VISIBLE) {
            buttonAnimation = -1;
            buttonLayout.animate().setListener(null).cancel();
            buttonLayout.animate().translationY(buttonHeight).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    buttonAnimation = 0;
                    buttonLayout.setVisibility(View.GONE);
                }
            }).start();

            RecyclerListView list = searchViewContainer.getVisibility() == View.VISIBLE ? searchListView : listView;
            list.hideSelector(false);

            int last = ((LinearLayoutManager) list.getLayoutManager()).findLastVisibleItemPosition();
            if (last == list.getAdapter().getItemCount() - 1 || (last == list.getAdapter().getItemCount() - 2 && list == listView)) {
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(last);
                if (holder != null) {
                    int bottom = holder.itemView.getBottom();
                    if (last == adapter.getItemCount() - 2) {
                        bottom += AndroidUtilities.dp(12);
                    }
                    if (list.getMeasuredHeight() - bottom <= buttonHeight) {
                        int dy = -(list.getMeasuredHeight() - bottom);
                        list.setTranslationY(dy);
                        list.animate().translationY(0).setDuration(200).start();
                    }
                }
            }
            listView.setPadding(0, 0, 0, 0);
            searchListView.setPadding(0, 0, 0, 0);

        }
        if (!selectedIds.isEmpty() && buttonLayout.getVisibility() == View.GONE && buttonAnimation != 1) {
            buttonAnimation = 1;
            buttonLayout.setVisibility(View.VISIBLE);
            buttonLayout.setTranslationY(buttonHeight);
            buttonLayout.animate().setListener(null).cancel();
            buttonLayout.animate().translationY(0).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    buttonAnimation = 0;
                }
            }).start();

            listView.setPadding(0, 0, 0, buttonHeight - AndroidUtilities.dp(12));
            searchListView.setPadding(0, 0, 0, buttonHeight);
        }

        if (!selectedIds.isEmpty()) {
            buttonTextView.setText(LocaleController.formatString("LeaveChats", R.string.LeaveChats, LocaleController.formatPluralString("Chats", selectedIds.size())));
        }
    }

    private void loadInactiveChannels() {
        adapter.notifyDataSetChanged();
        enterProgress = 0f;
        AndroidUtilities.runOnUIThread(showProgressRunnable, 500);
        TLRPC.TL_channels_getInactiveChannels inactiveChannelsRequest = new TLRPC.TL_channels_getInactiveChannels();
        getConnectionsManager().sendRequest(inactiveChannelsRequest, ((response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_inactiveChats chats = (TLRPC.TL_messages_inactiveChats) response;
                final ArrayList<String> signatures = new ArrayList<>();
                for (int i = 0; i < chats.chats.size(); i++) {
                    TLRPC.Chat chat = chats.chats.get(i);
                    int currentDate = getConnectionsManager().getCurrentTime();
                    int date = chats.dates.get(i);
                    int daysDif = (currentDate - date) / 86400;

                    String dateFormat;
                    if (daysDif < 30) {
                        dateFormat = LocaleController.formatPluralString("Days", daysDif);
                    } else if (daysDif < 365) {
                        dateFormat = LocaleController.formatPluralString("Months", daysDif / 30);
                    } else {
                        dateFormat = LocaleController.formatPluralString("Years", daysDif / 365);
                    }
                    if (ChatObject.isMegagroup(chat)) {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    } else if (ChatObject.isChannel(chat)) {
                        signatures.add(LocaleController.formatString("InactiveChannelSignature", R.string.InactiveChannelSignature, dateFormat));
                    } else {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    inactiveChatsSignatures.clear();
                    inactiveChats.clear();
                    inactiveChatsSignatures.addAll(signatures);
                    inactiveChats.addAll(chats.chats);
                    adapter.notifyDataSetChanged();
                    if (listView.getMeasuredHeight() > 0) {
                        enterAnimator = ValueAnimator.ofFloat(0, 1f);
                        enterAnimator.addUpdateListener(animation -> {
                            enterProgress = (float) animation.getAnimatedValue();
                            int n = listView.getChildCount();
                            for (int i = 0; i < n; i++) {
                                if (listView.getChildAdapterPosition(listView.getChildAt(i)) >= adapter.headerPosition && adapter.headerPosition > 0) {
                                    listView.getChildAt(i).setAlpha(enterProgress);
                                } else {
                                    listView.getChildAt(i).setAlpha(1f);
                                }
                            }
                        });
                        enterAnimator.setDuration(100);
                        enterAnimator.start();
                    } else {
                        enterProgress = 1f;
                    }

                    AndroidUtilities.cancelRunOnUIThread(showProgressRunnable);
                    if (progressBar.getVisibility() == View.VISIBLE) {
                        progressBar.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                progressBar.setVisibility(View.GONE);
                            }
                        }).start();
                    }
                });
            }
        }));
    }

    class Adapter extends RecyclerListView.SelectionAdapter {

        int rowCount;
        int hintPosition;
        int shadowPosition;
        int headerPosition;
        int inactiveChatsStartRow;
        int inactiveChatsEndRow;
        int endPaddingPosition;

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            super.notifyDataSetChanged();
        }

        public void updateRows() {
            hintPosition = -1;
            shadowPosition = -1;
            headerPosition = -1;
            inactiveChatsStartRow = -1;
            inactiveChatsEndRow = -1;
            endPaddingPosition = -1;

            rowCount = 0;
            hintPosition = rowCount++;
            shadowPosition = rowCount++;

            if (!inactiveChats.isEmpty()) {
                headerPosition = rowCount++;
                inactiveChatsStartRow = rowCount++;
                rowCount += inactiveChats.size() - 1;
                inactiveChatsEndRow = rowCount;
                endPaddingPosition = rowCount++;
            }

        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    hintCell = new TooManyCommunitiesHintCell(parent.getContext());
                    view = hintCell;
                    String message;
                    if (type == TYPE_JOIN) {
                        message = LocaleController.getString(R.string.TooManyCommunitiesHintJoin);
                    } else if (type == TYPE_EDIT) {
                        message = LocaleController.getString(R.string.TooManyCommunitiesHintEdit);
                    } else {
                        message = LocaleController.getString(R.string.TooManyCommunitiesHintCreate);
                    }
                    hintCell.setMessageText(message);
                    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = AndroidUtilities.dp(16);
                    lp.topMargin = AndroidUtilities.dp(23);

                    hintCell.setLayoutParams(lp);
                    break;
                case 2:
                    view = new ShadowSectionCell(parent.getContext());
                    Drawable drawable = Theme.getThemedDrawableByKey(parent.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    final ColorDrawable grayDrawable = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(grayDrawable, drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackground(combinedDrawable);
                    ThemeBind.drawableColorFilter(view, grayDrawable, Theme.key_windowBackgroundGray, PorterDuff.Mode.SRC_IN);
                    break;
                case 3:
                    HeaderCell header = new HeaderCell(parent.getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 8, false);
                    view = header;
                    header.setHeight(54);
                    header.setText(LocaleController.getString(R.string.InactiveChats));
                    break;
                case 5:
                    view = new EmptyCell(parent.getContext(), AndroidUtilities.dp(12));
                    break;
                case 4:
                default:
                    view = new GroupCreateUserCell(parent.getContext(), 1, 0, false);
                    break;

            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position >= headerPosition && headerPosition > 0) {
                holder.itemView.setAlpha(enterProgress);
            } else {
                holder.itemView.setAlpha(1f);
            }
            if (getItemViewType(position) == 4) {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                TLRPC.Chat chat = inactiveChats.get(position - inactiveChatsStartRow);
                String signature = inactiveChatsSignatures.get(position - inactiveChatsStartRow);
                cell.setObject(chat, chat.title, signature, position != inactiveChatsEndRow - 1);
                cell.setChecked(selectedIds.contains(chat.id), false);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == hintPosition) {
                return 1;
            } else if (position == shadowPosition) {
                return 2;
            } else if (position == headerPosition) {
                return 3;
            } else if (position == endPaddingPosition) {
                return 5;
            }
            return 4;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() >= inactiveChatsStartRow && holder.getAdapterPosition() < inactiveChatsEndRow) {
                return true;
            }
            return false;
        }
    }

    class SearchAdapter extends RecyclerListView.SelectionAdapter {

        ArrayList<TLRPC.Chat> searchResults = new ArrayList<>();
        ArrayList<String> searchResultsSignatures = new ArrayList<>();
        private Runnable searchRunnable;
        private int lastSearchId;

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new GroupCreateUserCell(parent.getContext(), 1, 0, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TLRPC.Chat chat = searchResults.get(position);
            String signature = searchResultsSignatures.get(position);
            GroupCreateUserCell cell = ((GroupCreateUserCell) holder.itemView);
            cell.setObject(chat, chat.title, signature, position != searchResults.size() - 1);
            cell.setChecked(selectedIds.contains(chat.id), false);
        }

        @Override
        public int getItemCount() {
            return searchResults.size();
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (TextUtils.isEmpty(query)) {
                searchResults.clear();
                searchResultsSignatures.clear();
                notifyDataSetChanged();
                emptyView.setVisibility(View.GONE);
            } else {
                int searchId = ++lastSearchId;
                Utilities.searchQueue.postRunnable(searchRunnable = () -> processSearch(query, searchId), 300);
            }
        }

        public void processSearch(String query, int id) {
            Utilities.searchQueue.postRunnable(() -> {
                String search1 = query.trim().toLowerCase();
                if (search1.length() == 0) {
                    updateSearchResults(null, null, id);
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

                ArrayList<TLRPC.Chat> resultArray = new ArrayList<>();
                ArrayList<String> resultArraySignatures = new ArrayList<>();

                for (int a = 0; a < inactiveChats.size(); a++) {
                    TLRPC.Chat chat = inactiveChats.get(a);
                    boolean found = false;
                    for (int i = 0; i < 2; i++) {
                        String name = i == 0 ? chat.title : ChatObject.getPublicUsername(chat);
                        if (name == null) {
                            continue;
                        }
                        name = name.toLowerCase();
                        for (String q : search) {
                            if ((name.startsWith(q) || name.contains(" " + q))) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            resultArray.add(chat);
                            resultArraySignatures.add(inactiveChatsSignatures.get(a));
                            break;
                        }
                    }
                }

                updateSearchResults(resultArray, resultArraySignatures, id);
            });
        }

        private void updateSearchResults(ArrayList<TLRPC.Chat> chats, ArrayList<String> signatures, int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                searchResults.clear();
                searchResultsSignatures.clear();
                if (chats != null) {
                    searchResults.addAll(chats);
                    searchResultsSignatures.addAll(signatures);
                }
                notifyDataSetChanged();
                if (searchResults.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    emptyView.setVisibility(View.GONE);
                }
            });

        }
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::cellDelegateThemeColors;

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));



        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

        themeDescriptions.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxDisabled));
        themeDescriptions.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

        themeDescriptions.add(new ThemeDescription(buttonTextView, 0, null, null, null, cellDelegate, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(buttonTextView, 0, null, null, null, cellDelegate, Theme.key_featuredStickers_addButtonPressed));

        return themeDescriptions;
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            cellDelegateThemeColors();
            updateShadowSectionColors();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void updateShadowSectionColors() {
        try {
            if (listView == null) {
                return;
            }
            final int shadowColor = Theme.getColor(Theme.key_windowBackgroundGrayShadow);
            if (shadowFilter == null || shadowFilterColor != shadowColor) {
                shadowFilterColor = shadowColor;
                shadowFilter = new PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY);
            }
            for (int a = 0; a < listView.getChildCount(); a++) {
                final View child = listView.getChildAt(a);
                if (child instanceof ShadowSectionCell && child.getBackground() instanceof CombinedDrawable) {
                    Theme.setCombinedDrawableColor(child.getBackground(), Theme.getColor(Theme.key_windowBackgroundGray), false);
                    final Drawable icon = ((CombinedDrawable) child.getBackground()).getIcon();
                    if (icon != null) {
                        icon.setColorFilter(shadowFilter);
                    }
                    child.invalidate();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private PorterDuffColorFilter shadowFilter;
    private int shadowFilterColor;
    private int lastButtonColor;
    private boolean lastButtonColorDark;

    private void cellDelegateThemeColors() {
        if (buttonTextView != null) {
            final int color = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (color != lastButtonColor || lastButtonColorDark != Theme.isCurrentThemeDark()) {
                lastButtonColorDark = Theme.isCurrentThemeDark(); // the ripple shade depends on dark / light
                lastButtonColor = color;
                // recolor in place: content layer and ripple of the AdaptiveRipple.filledRect background
                Theme.setSelectorDrawableColor(buttonTextView.getBackground(), color, false);
                Theme.setSelectorDrawableColor(buttonTextView.getBackground(), Theme.AdaptiveRipple.calcRippleColor(color), true);
            }
        }
        if (progressBar != null) {
            progressBar.setProgressColor(Theme.getColor(Theme.key_progressCircle));
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_text_RedRegular);
        keys.add(Theme.key_featuredStickers_addButtonPressed);
        keys.add(Theme.key_avatar_backgroundPink);
        keys.add(Theme.key_avatar_backgroundBlue);
        keys.add(Theme.key_avatar_backgroundCyan);
        keys.add(Theme.key_avatar_backgroundGreen);
        keys.add(Theme.key_avatar_backgroundViolet);
        keys.add(Theme.key_avatar_backgroundOrange);
        keys.add(Theme.key_avatar_backgroundRed);
        keys.add(Theme.key_avatar_text);
        keys.add(Theme.key_windowBackgroundWhiteGrayText);
        keys.add(Theme.key_windowBackgroundWhiteBlackText);
        keys.add(Theme.key_windowBackgroundWhiteBlueHeader);
        keys.add(Theme.key_windowBackgroundGray);
        keys.add(Theme.key_windowBackgroundGrayShadow);
        keys.add(Theme.key_chats_message);
        keys.add(Theme.key_chats_nameMessage_threeLines);
        keys.add(Theme.key_actionBarDefaultTitle);
        keys.add(Theme.key_actionBarDefaultIcon);
        keys.add(Theme.key_actionBarDefaultSelector);
        keys.add(Theme.key_actionBarDefault);
    }
}

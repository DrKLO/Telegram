/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.util.ArrayList;

public class ThemePreviewActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout page1;
    private RecyclerListView listView;
    private DialogsAdapter dialogsAdapter;
    private ImageView floatingButton;
    private View dotsContainer;
    private ActionBar actionBar2;

    private SizeNotifierFrameLayout page2;
    private RecyclerListView listView2;
    private MessagesAdapter messagesAdapter;

    private Theme.ThemeInfo applyingTheme;
    private File themeFile;
    private boolean applied;

    public ThemePreviewActivity(File file, Theme.ThemeInfo themeInfo) {
        super();
        swipeBackEnabled = false;
        applyingTheme = themeInfo;
        themeFile = file;
    }

    @Override
    public View createView(Context context) {
        page1 = new FrameLayout(context);
        ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {

            }

            @Override
            public boolean canCollapseSearch() {
                return true;
            }

            @Override
            public void onSearchCollapse() {

            }

            @Override
            public void onTextChanged(EditText editText) {

            }
        });
        item.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        actionBar.setBackButtonDrawable(new MenuDrawable());
        actionBar.setAddToContainer(false);
        actionBar.setTitle(LocaleController.getString("ThemePreview", R.string.ThemePreview));

        page1 = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));

                measureChildWithMargins(floatingButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() : 0);
                }
                return result;
            }
        };
        page1.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(true);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        page1.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

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
        floatingButton.setImageResource(R.drawable.floating_pencil);
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
        page1.addView(floatingButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));

        dialogsAdapter = new DialogsAdapter(context);
        listView.setAdapter(dialogsAdapter);

        page2 = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar2, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar2.getMeasuredHeight();
                if (actionBar2.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView2.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView2.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar2 && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar2.getVisibility() == VISIBLE ? actionBar2.getMeasuredHeight() : 0);
                }
                return result;
            }
        };
        page2.setBackgroundImage(Theme.getCachedWallpaper());

        actionBar2 = createActionBar(context);
        actionBar2.setBackButtonDrawable(new BackDrawable(false));
        actionBar2.setTitle("Reinhardt");
        actionBar2.setSubtitle(LocaleController.formatDateOnline(System.currentTimeMillis() / 1000 - 60 * 60));
        page2.addView(actionBar2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView2 = new RecyclerListView(context);
        listView2.setVerticalScrollBarEnabled(true);
        listView2.setItemAnimator(null);
        listView2.setLayoutAnimation(null);
        listView2.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView2.setClipToPadding(false);
        listView2.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView2.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        page2.addView(listView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        messagesAdapter = new MessagesAdapter(context);
        listView2.setAdapter(messagesAdapter);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        final ViewPager viewPager = new ViewPager(context);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                dotsContainer.invalidate();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        viewPager.setAdapter(new PagerAdapter() {

            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return object == view;
            }

            @Override
            public int getItemPosition(Object object) {
                return POSITION_UNCHANGED;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View view = position == 0 ? page1 : page2;
                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public void unregisterDataSetObserver(DataSetObserver observer) {
                if (observer != null) {
                    super.unregisterDataSetObserver(observer);
                }
            }
        });
        AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, Theme.getColor(Theme.key_actionBarDefault));
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        frameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

        FrameLayout bottomLayout = new FrameLayout(context);
        bottomLayout.setBackgroundColor(0xffffffff);
        frameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        dotsContainer = new View(context) {

            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                int selected = viewPager.getCurrentItem();
                for (int a = 0; a < 2; a++) {
                    paint.setColor(a == selected ? 0xff999999 : 0xffcccccc);
                    canvas.drawCircle(AndroidUtilities.dp(3 + 15 * a), AndroidUtilities.dp(4), AndroidUtilities.dp(3), paint);
                }
            }
        };
        bottomLayout.addView(dotsContainer, LayoutHelper.createFrame(22, 8, Gravity.CENTER));

        TextView cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(0xff19a7e8);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x2f000000, 0));
        cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomLayout.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Theme.applyPreviousTheme();
                parentLayout.rebuildAllFragmentViews(false, false);
                finishFragment();
            }
        });

        TextView doneButton = new TextView(context);
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButton.setTextColor(0xff19a7e8);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x2f000000, 0));
        doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        doneButton.setText(LocaleController.getString("ApplyTheme", R.string.ApplyTheme).toUpperCase());
        doneButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applied = true;
                parentLayout.rebuildAllFragmentViews(false, false);
                Theme.applyThemeFile(themeFile, applyingTheme.name, false);
                finishFragment();
            }
        });

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dialogsAdapter != null) {
            dialogsAdapter.notifyDataSetChanged();
        }
        if (messagesAdapter != null) {
            messagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onBackPressed() {
        Theme.applyPreviousTheme();
        parentLayout.rebuildAllFragmentViews(false, false);
        return super.onBackPressed();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (listView == null) {
                return;
            }
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof DialogCell) {
                    DialogCell cell = (DialogCell) child;
                    cell.update(0);
                }
            }
        }
    }

    public class DialogsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<DialogCell.CustomDialog> dialogs;

        public DialogsAdapter(Context context) {
            mContext = context;
            dialogs = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000);
            DialogCell.CustomDialog customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Eva Summer";
            customDialog.message = "Reminds me of a Chinese prove...";
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = true;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Alexandra Smith";
            customDialog.message = "Reminds me of a Chinese prove...";
            customDialog.id = 1;
            customDialog.unread_count = 2;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Make Apple";
            customDialog.message = "\uD83E\uDD37\u200D♂️ Sticker";
            customDialog.id = 2;
            customDialog.unread_count = 3;
            customDialog.pinned = false;
            customDialog.muted = true;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 2;
            customDialog.verified = false;
            customDialog.isMedia = true;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Paul Newman";
            customDialog.message = "Any ideas?";
            customDialog.id = 3;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 2;
            customDialog.date = date - 60 * 60 * 3;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Old Pirates";
            customDialog.message = "Yo-ho-ho!";
            customDialog.id = 4;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 1;
            customDialog.date = date - 60 * 60 * 4;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Kate Bright";
            customDialog.message = "Hola!";
            customDialog.id = 5;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 5;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Nick K";
            customDialog.message = "These are not the droids you are looking for";
            customDialog.id = 6;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 6;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = "Adler Toberg";
            customDialog.message = "Did someone say peanut butter?";
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 7;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);
        }

        @Override
        public int getItemCount() {
            return dialogs.size() + 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new DialogCell(mContext, false);
            } else if (viewType == 1) {
                view = new LoadingCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == 0) {
                DialogCell cell = (DialogCell) viewHolder.itemView;
                cell.useSeparator = (i != getItemCount() - 1);
                cell.setDialog(dialogs.get(i));
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == dialogs.size()) {
                return 1;
            }
            return 0;
        }
    }

    public class MessagesAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<MessageObject> messages;

        public MessagesAdapter(Context context) {
            mContext = context;
            messages = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.Message message;

            message = new TLRPC.TL_message();
            message.message = "Reinhardt, we need to find you some new tunes \uD83C\uDFB6.";
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            MessageObject replyMessageObject = new MessageObject(message, null, true);

            message = new TLRPC.TL_message();
            message.message = "I can't even take you seriously right now.";
            message.date = date + 960;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            MessageObject messageObject;
            messages.add(new MessageObject(message, null, true));

            message = new TLRPC.TL_message();
            message.date = date + 130;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = 0;
            message.id = 5;
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.flags |= 3;
            message.media.document = new TLRPC.TL_document();
            message.media.document.mime_type = "audio/mp4";
            message.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
            message.media.document.thumb.type = "s";
            TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
            audio.duration = 243;
            audio.performer = "David Hasselhoff";
            audio.title = "True Survivor";
            message.media.document.attributes.add(audio);
            message.out = false;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = UserConfig.getClientUserId();
            messages.add(new MessageObject(message, null, true));

            message = new TLRPC.TL_message();
            message.message = "Ah, you kids today with techno music! You should enjoy the classics, like Hasselhoff!";
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + 8;
            message.from_id = 0;
            message.id = 1;
            message.reply_to_msg_id = 5;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = UserConfig.getClientUserId();
            messageObject = new MessageObject(message, null, true);
            messageObject.customReplyName = "Lucio";
            messageObject.replyMessageObject = replyMessageObject;
            messages.add(messageObject);

            message = new TLRPC.TL_message();
            message.date = date + 120;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.flags |= 3;
            message.media.document = new TLRPC.TL_document();
            message.media.document.mime_type = "audio/ogg";
            message.media.document.thumb = new TLRPC.TL_photoSizeEmpty();
            message.media.document.thumb.type = "s";
            audio = new TLRPC.TL_documentAttributeAudio();
            audio.flags = 1028;
            audio.duration = 3;
            audio.voice = true;
            audio.waveform = new byte[]{0, 4, 17, -50, -93, 86, -103, -45, -12, -26, 63, -25, -3, 109, -114, -54, -4, -1,
                        -1, -1, -1, -29, -1, -1, -25, -1, -1, -97, -43, 57, -57, -108, 1, -91, -4, -47, 21, 99, 10, 97, 43,
                        45, 115, -112, -77, 51, -63, 66, 40, 34, -122, -116, 48, -124, 16, 66, -120, 16, 68, 16, 33, 4, 1};
            message.media.document.attributes.add(audio);
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            messageObject = new MessageObject(message, null, true);
            messageObject.audioProgressSec = 1;
            messageObject.audioProgress = 0.3f;
            messageObject.useCustomPhoto = true;
            messages.add(messageObject);

            messages.add(replyMessageObject);

            message = new TLRPC.TL_message();
            message.date = date + 10;
            message.dialog_id = 1;
            message.flags = 257;
            message.from_id = 0;
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaPhoto();
            message.media.flags |= 3;
            message.media.photo = new TLRPC.TL_photo();
            message.media.photo.has_stickers = false;
            message.media.photo.id = 1;
            message.media.photo.access_hash = 0;
            message.media.photo.date = date;
            TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
            photoSize.size = 0;
            photoSize.w = 500;
            photoSize.h = 302;
            photoSize.type = "s";
            photoSize.location = new TLRPC.TL_fileLocationUnavailable();
            message.media.photo.sizes.add(photoSize);
            message.media.caption = "Bring it on! I LIVE for this!";
            message.out = false;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = UserConfig.getClientUserId();
            messageObject = new MessageObject(message, null, true);
            messageObject.useCustomPhoto = true;
            messages.add(messageObject);

            message = new TLRPC.TL_message();
            message.message = LocaleController.formatDateChat(date);
            message.id = 0;
            message.date = date;
            messageObject = new MessageObject(message, null, false);
            messageObject.type = 10;
            messageObject.contentType = 1;
            messageObject.isDateObject = true;
            messages.add(messageObject);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new ChatMessageCell(mContext);
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                    @Override
                    public void didPressedShare(ChatMessageCell cell) {

                    }

                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        return false;
                    }

                    @Override
                    public void didPressedChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId) {

                    }

                    @Override
                    public void didPressedOther(ChatMessageCell cell) {

                    }

                    @Override
                    public void didPressedUserAvatar(ChatMessageCell cell, TLRPC.User user) {

                    }

                    @Override
                    public void didPressedBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {

                    }

                    @Override
                    public void didPressedCancelSendButton(ChatMessageCell cell) {

                    }

                    @Override
                    public void didLongPressed(ChatMessageCell cell) {

                    }

                    @Override
                    public boolean canPerformActions() {
                        return false;
                    }

                    @Override
                    public void didPressedUrl(MessageObject messageObject, final CharacterStyle url, boolean longPress) {

                    }

                    @Override
                    public void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h) {

                    }

                    @Override
                    public void didPressedReplyMessage(ChatMessageCell cell, int id) {

                    }

                    @Override
                    public void didPressedViaBot(ChatMessageCell cell, String username) {

                    }

                    @Override
                    public void didPressedImage(ChatMessageCell cell) {

                    }

                    @Override
                    public void didPressedInstantButton(ChatMessageCell cell, int type) {

                    }

                    @Override
                    public boolean isChatAdminCell(int uid) {
                        return false;
                    }
                });
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickedImage(ChatActionCell cell) {

                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {

                    }

                    @Override
                    public void needOpenUserProfile(int uid) {

                    }

                    @Override
                    public void didPressedReplyMessage(ChatActionCell cell, int id) {

                    }

                    @Override
                    public void didPressedBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {

                    }
                });
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            MessageObject message = messages.get(position);
            View view = holder.itemView;

            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                messageCell.isChat = false;
                int nextType = getItemViewType(position - 1);
                int prevType = getItemViewType(position + 1);
                boolean pinnedBotton;
                boolean pinnedTop;
                if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                    MessageObject nextMessage = messages.get(position - 1);
                    pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedBotton = false;
                }
                if (prevType == holder.getItemViewType()) {
                    MessageObject prevMessage = messages.get(position + 1);
                    pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedTop = false;
                }
                messageCell.setFullyDraw(true);
                messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
            } else if (view instanceof ChatActionCell) {
                ChatActionCell actionCell = (ChatActionCell) view;
                actionCell.setMessageObject(message);
                actionCell.setAlpha(1.0f);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= 0 && i < messages.size()) {
                return messages.get(i).contentType;
            }
            return 4;
        }
    }
}

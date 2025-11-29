package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.distance;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.FilledTabsView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Gifts.ResaleGiftsFragment;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.StoriesUtilities;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PeerColorActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final boolean isChannel;
    private final long dialogId;

    private final StarsController.GiftsList gifts;
    private final StarsController.GiftsList giftsWithPeerColor;

    private FrameLayout contentView;
    private ColoredActionBar colorBar;

    public static final int PAGE_PROFILE = 0;
    public static final int PAGE_NAME = 1;

    public Page namePage;
    public Page profilePage;

    public Page getCurrentPage() {
        return viewPager.getCurrentPosition() == PAGE_PROFILE ? profilePage : namePage;
    }

    public boolean loading;

    private class Page extends FrameLayout {

        private ProfilePreview profilePreview;

        private RecyclerListView listView;
        private GridLayoutManager layoutManager;
        private RecyclerView.Adapter listAdapter;
        private View buttonShadow;
        private FrameLayout buttonContainer;
        private ButtonWithCounterView button;
        private PeerColorGrid peerColorPicker;

        private int selectedColor = -1;
        private long selectedEmoji = 0;
        private TLRPC.TL_emojiStatusCollectible selectedEmojiCollectible = null;
        private TLRPC.TL_peerColorCollectible selectedPeerCollectible = null;
        private ThemePreviewMessagesCell messagesCellPreview;
        private SetReplyIconCell setReplyIconCell;
        private TL_stars.TL_starGiftUnique selectedResaleGift;
        private ResaleGiftsFragment.ResaleGiftsList resaleGifts;
        private TL_stars.StarGift selectedTabGift = null;

        private final ArrayList<CharSequence> tabs = new ArrayList<>();
        private final HashMap<Integer, TL_stars.StarGift> index2gift = new HashMap<>();

        private CharSequence buttonLocked, buttonUnlocked, buttonCollectible;

        int colorPickerRow = -1;
        int infoRow = -1;
        int iconRow = -1;
        int info2Row = -1;
        int buttonRow = -1;
        int clearRow = -1;
        int shadowRow = -1;
        int giftsHeaderRow = -1;
        int giftsStartRow = -1;
        int giftsEndRow = -1;
        int giftsLoadingStartRow = -1;
        int giftsLoadingEndRow = -1;
        int giftsCount = 0;
        int giftsInfoRow = -1;
        int giftsTabsRow = -1;
        int giftsEmptyRow = -1;
        int rowCount;
        final ArrayList<TL_stars.TL_starGiftUnique> uniqueGifts = new ArrayList<>();

        private static final int VIEW_TYPE_MESSAGE = 0;
        private static final int VIEW_TYPE_COLOR_PICKER = 1;
        private static final int VIEW_TYPE_INFO = 2;
        private static final int VIEW_TYPE_ICON = 3;
        private static final int VIEW_TYPE_BUTTONPAD = 5;
        private static final int VIEW_TYPE_TEXT = 6;
        private static final int VIEW_TYPE_HEADER = 7;
        private static final int VIEW_TYPE_GIFT = 8;
        private static final int VIEW_TYPE_FLICKER = 9;
        private static final int VIEW_TYPE_TABS = 10;
        private static final int VIEW_TYPE_GIFTS_EMPTY = 11;
        private static final int VIEW_TYPE_GIFT_FOREIGN = 12;

        public void setupValues() {
            if (type == PAGE_PROFILE) {
                if (dialogId < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    selectedColor = ChatObject.getProfileColorId(chat);
                    selectedEmoji = ChatObject.getProfileEmojiId(chat);
                    selectedEmojiCollectible = chat != null && chat.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? (TLRPC.TL_emojiStatusCollectible) chat.emoji_status : null;
                    selectedPeerCollectible = null;
                } else {
                    TLRPC.User user = getUserConfig().getCurrentUser();
                    selectedColor = UserObject.getProfileColorId(user);
                    selectedEmoji = UserObject.getProfileEmojiId(user);
                    selectedEmojiCollectible = user != null && user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? (TLRPC.TL_emojiStatusCollectible) user.emoji_status : null;
                    selectedPeerCollectible = null;
                }
            } else {
                if (dialogId < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    selectedColor = ChatObject.getColorId(chat);
                    selectedEmoji = ChatObject.getEmojiId(chat);
                    selectedEmojiCollectible = null;
                    selectedPeerCollectible = chat != null && chat.color instanceof TLRPC.TL_peerColorCollectible ? (TLRPC.TL_peerColorCollectible) chat.color : null;
                } else {
                    TLRPC.User user = getUserConfig().getCurrentUser();
                    selectedColor = UserObject.getColorId(user);
                    selectedEmoji = UserObject.getEmojiId(user);
                    selectedEmojiCollectible = null;
                    selectedPeerCollectible = user != null && user.color instanceof TLRPC.TL_peerColorCollectible ? (TLRPC.TL_peerColorCollectible) user.color : null;
                }
            }
            if (selectedEmojiCollectible != null || selectedPeerCollectible != null) {
                selectedColor = -1;
                selectedEmoji = 0;
            }

        }

        private final int type;
        public Page(Context context, int type) {
            super(context);
            this.type = type;
            this.setupValues();

            listView = new RecyclerListView(getContext(), getResourceProvider()) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, heightSpec);
                    updateButtonY();
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    updateButtonY();
                    if (selectedTabGift != null) {
                        if (resaleGifts != null && seesLoading()) {
                            resaleGifts.load();
                        }
                    } else {
                        final StarsController.GiftsList giftsList = type == PAGE_NAME ? giftsWithPeerColor : gifts;
                        if (giftsList != null && seesLoading()) {
                            giftsList.load();
                        }
                    }
                }

                @Override
                public void onDraw(Canvas c) {
                    drawSectionBackground(c, giftsStartRow, Math.max(giftsLoadingEndRow, giftsEndRow) - 1, getThemedColor(Theme.key_windowBackgroundWhite), dp(8), dp(8));
                    super.onDraw(c);
                }

                @Override
                public Integer getSelectorColor(int position) {
                    if (position >= giftsStartRow && position < giftsEndRow || position >= giftsLoadingStartRow && position < giftsLoadingEndRow) {
                        return 0x00000000;
                    }
                    return super.getSelectorColor(position);
                }
            };
            listView.setClipToPadding(false);
            ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
            layoutManager = new GridLayoutManager(getContext(), 3);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position >= giftsStartRow && position < giftsEndRow) {
                        return 1;
                    }
                    if (position >= giftsLoadingStartRow && position < giftsLoadingEndRow) {
                        return 1;
                    }
                    return 3;
                }
            });
            listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    final int position = parent.getChildAdapterPosition(view);
                    if (position < giftsStartRow || position >= giftsStartRow + giftsCount) return;
                    final int index = position - giftsStartRow;
                    final boolean up = index / 3 == 0, down = index / 3 == (giftsCount - 1) / 3;
                    final boolean left = index % 3 == 0, right = index % 3 == 2;
                    outRect.top = up ? dp(8) : 0;
                    outRect.bottom = down ? dp(8) : 0;
                    outRect.left = left ? dp(8) : 0;
                    outRect.right = right ? dp(8) : 0;
                }
            });
            listView.setLayoutManager(layoutManager);
            listView.setAdapter(listAdapter = new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return holder.getItemViewType() == VIEW_TYPE_ICON || holder.getItemViewType() == VIEW_TYPE_TEXT || holder.getItemViewType() == VIEW_TYPE_GIFT || holder.getItemViewType() == VIEW_TYPE_GIFT_FOREIGN;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view;
                    switch (viewType) {
//                        case VIEW_TYPE_MESSAGE:
//                            ThemePreviewMessagesCell messagesCell = messagesCellPreview = new ThemePreviewMessagesCell(getContext(), parentLayout, ThemePreviewMessagesCell.TYPE_PEER_COLOR, dialogId, resourceProvider);
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                                messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
//                            }
//                            messagesCell.fragment = PeerColorActivity.this;
//                            view = messagesCell;
//                            break;
                        case VIEW_TYPE_HEADER:
                            HeaderCell headerCell = new HeaderCell(getContext(), resourceProvider);
                            headerCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            view = headerCell;
                            break;
                        case VIEW_TYPE_GIFT:
                            GiftCell giftCell = new GiftCell(getContext(), false, resourceProvider);
                            view = giftCell;
                            break;
                        case VIEW_TYPE_GIFT_FOREIGN:
                            GiftSheet.GiftCell giftCell2 = new GiftSheet.GiftCell(getContext(), currentAccount, resourceProvider);
                            view = giftCell2;
                            break;
                        case VIEW_TYPE_FLICKER:
                            FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, resourceProvider);
                            flickerLoadingView.setIsSingleCell(true);
                            flickerLoadingView.setViewType(FlickerLoadingView.STAR_GIFT_SELECT);
                            view = flickerLoadingView;
                            break;
                        default:
                        case VIEW_TYPE_INFO:
                            TextInfoPrivacyCell cell = new TextInfoPrivacyCell(getContext(), getResourceProvider());
                            view = cell;
                            break;
                        case VIEW_TYPE_COLOR_PICKER:
                            PeerColorGrid colorPicker = peerColorPicker = new PeerColorGrid(getContext(), type, currentAccount, resourceProvider);
                            colorPicker.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            colorPicker.setSelected(selectedColor, false);
                            colorPicker.setOnColorClick(colorId -> {
                                selectedColor = colorId;
                                selectedEmojiCollectible = null;
                                selectedPeerCollectible = null;
                                selectedResaleGift = null;
                                updateProfilePreview(true);
                                updateMessages();
                                updateButton(true);
                                if (setReplyIconCell != null) {
                                    setReplyIconCell.invalidate();
                                }
                                if (profilePage != null && profilePage.profilePreview != null && namePage != null) {
                                    profilePage.profilePreview.overrideAvatarColor(namePage.selectedColor);
                                }
                            });
                            view = colorPicker;
                            break;
                        case VIEW_TYPE_BUTTONPAD:
                            view = new View(getContext()) {
                                @Override
                                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(14 + 48 + 14), MeasureSpec.EXACTLY));
                                }
                            };
                            break;
                        case VIEW_TYPE_ICON:
                            SetReplyIconCell setcell = setReplyIconCell = new SetReplyIconCell(getContext());
                            setcell.update(false);
                            view = setcell;
                            break;
                        case VIEW_TYPE_TEXT:
                            TextCell textCell = new TextCell(getContext(), getResourceProvider());
                            textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            view = textCell;
                            break;
                        case 4:
                            view = new View(getContext()) {
                                @Override
                                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                    super.onMeasure(
                                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                            MeasureSpec.makeMeasureSpec(dp(16), MeasureSpec.EXACTLY)
                                    );
                                }
                            };
                            view.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            break;
                        case VIEW_TYPE_TABS:
                            view = new GiftSheet.Tabs(getContext(), false, resourceProvider);
                            view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            break;
                        case VIEW_TYPE_GIFTS_EMPTY:
                            LinearLayout layout = new LinearLayout(getContext());
                            layout.setOrientation(LinearLayout.VERTICAL);
                            layout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                            BackupImageView imageView = new BackupImageView(getContext());
                            imageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_draw, "utyan_draw", dp(120), dp(120)));
                            layout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));

                            TextView title = TextHelper.makeLinkTextView(getContext(), 14, Theme.key_windowBackgroundWhiteGrayText, false, resourceProvider);
                            title.setGravity(Gravity.CENTER);
                            title.setText(getString(type == PAGE_PROFILE ? R.string.Gift2PeerColorProfileEmptyTitle : R.string.Gift2PeerColorReplyEmptyTitle));
                            layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 64, 8, 64, 8));

                            TextView subtitle = TextHelper.makeLinkTextView(getContext(), 14, Theme.key_chat_messageLinkIn, false, resourceProvider);
                            subtitle.setGravity(Gravity.CENTER);
                            subtitle.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2PeerColorEmptyButton), () -> {
                                GiftSheet.Tabs tabs = null;
                                for (int i = 0; i < listView.getChildCount(); ++i) {
                                    final View child = listView.getChildAt(i);
                                    if (child instanceof GiftSheet.Tabs) {
                                        tabs = (GiftSheet.Tabs) child;
                                    }
                                }
                                if (tabs != null && Page.this.tabs.size() > 1) {
                                    tabs.setSelected(1, true);
                                    selectedTabGift = index2gift.get(1);
                                    if (selectedTabGift == null) {
                                        if (resaleGifts != null) {
                                            resaleGifts.cancel();
                                            resaleGifts = null;
                                        }
                                    } else if (resaleGifts == null || resaleGifts.gift_id != selectedTabGift.id) {
                                        resaleGifts = new ResaleGiftsFragment.ResaleGiftsList(currentAccount, selectedTabGift.id, first -> update());
                                        resaleGifts.load();
                                    }
                                    update();
                                    final Page otherPage = viewPager.getCurrentPosition() == PAGE_NAME ? profilePage : namePage;
                                    otherPage.update();
                                }
                            }), true, dp(8f / 3f), dp(1.33f), 1.0f));
                            layout.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 4, 32, 24));

                            view = layout;
                            break;
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    switch (getItemViewType(position)) {
                        case VIEW_TYPE_INFO:
                            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                            cell.setFixedSize(0);
                            if (position == infoRow) {
                                String text;
                                if (type == PAGE_NAME) {
                                    text = getString(isChannel ? R.string.ChannelColorHint : R.string.UserColorHint);
                                } else {
                                    text = getString(isChannel ? R.string.ChannelProfileHint : R.string.UserProfileHint2);
                                }
                                cell.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(text, () -> {
                                    viewPager.scrollToPosition(1 - type);
                                }), true));
                                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), clearRow >= 0 ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            } else if (position == shadowRow) {
                                cell.setText("");
                                cell.setFixedSize(12);
                                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), giftsHeaderRow >= 0 ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            } else if (position == giftsInfoRow) {
                                cell.setText(getString(R.string.UserProfileCollectibleInfo));
                                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            }
                            break;
                        case VIEW_TYPE_TEXT:
                            TextCell textCell = (TextCell) holder.itemView;
                            textCell.updateColors();
                            textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                            if (position == clearRow) {
                                textCell.setText(getString(isChannel ? R.string.ChannelProfileColorReset : R.string.UserProfileColorReset), false);
                            }
                            break;
                        case VIEW_TYPE_HEADER:
                            HeaderCell headerCell = (HeaderCell) holder.itemView;
                            if (position == giftsHeaderRow) {
                                headerCell.setText(getString(R.string.UserProfileCollectibleHeader), false);
                            }
                            break;
                        case VIEW_TYPE_GIFT:
                            GiftCell giftCell = (GiftCell) holder.itemView;
                            final int index = position - giftsStartRow;
                            if (index < 0 || index >= uniqueGifts.size()) return;
                            final TL_stars.TL_starGiftUnique gift = uniqueGifts.get(index);
                            giftCell.set(index, gift);
                            giftCell.setSelected(
                                selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == gift.id ||
                                selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == gift.id,
                                false
                            );
                            break;
                        case VIEW_TYPE_GIFT_FOREIGN:
                            GiftSheet.GiftCell giftCell2 = (GiftSheet.GiftCell) holder.itemView;
                            final int index2 = position - giftsStartRow;
                            if (resaleGifts == null) return;
                            if (index2 < 0 || index2 >= uniqueGifts.size()) return;
                            final TL_stars.TL_starGiftUnique gift2 = uniqueGifts.get(index2);
                            giftCell2.setStarsGift(gift2, false, false, false, true);
                            giftCell2.setSelected(
                                selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == gift2.id ||
                                selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == gift2.id,
                                false
                            );
                            break;
                        case VIEW_TYPE_TABS:
                            GiftSheet.Tabs tabsView = (GiftSheet.Tabs) holder.itemView;
                            tabs.clear();
                            index2gift.clear();
                            final ArrayList<TL_stars.StarGift> gifts = StarsController.getInstance(currentAccount).sortedGifts;
                            tabs.add(getString(R.string.Gift2TabMine));
                            int selectedTab = 0;
                            for (int i = 0; i < gifts.size(); ++i) {
                                final TL_stars.StarGift starGift = gifts.get(i);
                                if ((type == PAGE_PROFILE || type == PAGE_NAME && starGift.peer_color_available) && starGift.availability_resale > 0) {
                                    if (selectedTabGift == starGift) {
                                        selectedTab = tabs.size();
                                    }
                                    index2gift.put(tabs.size(), starGift);
                                    final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                                    textPaint.setTextSize(dp(14));
                                    final SpannableStringBuilder sb = new SpannableStringBuilder("x ");
                                    final AnimatedEmojiSpan span = new AnimatedEmojiSpan(starGift.getDocument(), textPaint.getFontMetricsInt());
                                    span.size = dp(14);
                                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    sb.append(starGift.title);
                                    tabs.add(sb);
                                }
                            }
                            tabsView.set(0, tabs, selectedTab, tabIndex -> {
                                selectedTabGift = tabIndex == 0 ? null : index2gift.get(tabIndex);
                                if (selectedTabGift == null) {
                                    if (resaleGifts != null) {
                                        resaleGifts.cancel();
                                        resaleGifts = null;
                                    }
                                } else if (resaleGifts == null || resaleGifts.gift_id != selectedTabGift.id) {
                                    resaleGifts = new ResaleGiftsFragment.ResaleGiftsList(currentAccount, selectedTabGift.id, first -> update());
                                    resaleGifts.load();
                                }
                                update();
                                final Page otherPage = viewPager.getCurrentPosition() == PAGE_NAME ? profilePage : namePage;
                                otherPage.update();
                            });
                            break;
                    }
                }

                @Override
                public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                    super.onViewAttachedToWindow(holder);
                    if (holder.getItemViewType() == VIEW_TYPE_GIFT) {
                        GiftCell giftCell = (GiftCell) holder.itemView;
                        final int index = holder.getAdapterPosition() - giftsStartRow;
                        if (index < 0 || index >= uniqueGifts.size()) return;
                        final TL_stars.TL_starGiftUnique gift = uniqueGifts.get(index);
                        giftCell.set(index, gift);
                        giftCell.setSelected(
                            selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == gift.id ||
                            selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == gift.id,
                            false
                        );
                    } else if (holder.getItemViewType() == VIEW_TYPE_GIFT_FOREIGN) {
                        GiftSheet.GiftCell giftCell2 = (GiftSheet.GiftCell) holder.itemView;
                        final int index2 = holder.getAdapterPosition() - giftsStartRow;
                        if (resaleGifts == null) return;
                        if (index2 < 0 || index2 >= uniqueGifts.size()) return;
                        final TL_stars.TL_starGiftUnique gift2 = uniqueGifts.get(index2);
                        giftCell2.setStarsGift(gift2, false, false, false, true);
                        giftCell2.setSelected(
                            selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == gift2.id ||
                            selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == gift2.id,
                            false
                        );
                    }
                }

                @Override
                public int getItemCount() {
                    return rowCount;
                }

                @Override
                public int getItemViewType(int position) {
//                    if (position == previewRow) {
//                        return VIEW_TYPE_MESSAGE;
//                    }
                    if (position == infoRow || position == giftsInfoRow || position == info2Row || position == shadowRow) {
                        return VIEW_TYPE_INFO;
                    }
                    if (position == colorPickerRow) {
                        return VIEW_TYPE_COLOR_PICKER;
                    }
                    if (position == iconRow) {
                        return VIEW_TYPE_ICON;
                    }
                    if (position == buttonRow) {
                        return VIEW_TYPE_BUTTONPAD;
                    }
                    if (position == clearRow) {
                        return VIEW_TYPE_TEXT;
                    }
                    if (position == giftsTabsRow) {
                        return VIEW_TYPE_TABS;
                    }
                    if (position == giftsEmptyRow) {
                        return VIEW_TYPE_GIFTS_EMPTY;
                    }
                    if (position == giftsHeaderRow) {
                        return VIEW_TYPE_HEADER;
                    }
                    if (position >= giftsStartRow && position < giftsEndRow) {
                        if (selectedTabGift == null) {
                            return VIEW_TYPE_GIFT;
                        } else {
                            return VIEW_TYPE_GIFT_FOREIGN;
                        }
                    }
                    if (position >= giftsLoadingStartRow && position < giftsLoadingEndRow) {
                        return VIEW_TYPE_FLICKER;
                    }
                    if (position == getItemCount() - 1) {
                        return 4;
                    }
                    return VIEW_TYPE_INFO;
                }
            });
            listView.setOnItemClickListener((view, position) -> {
                if (view instanceof SetReplyIconCell) {
                    showSelectStatusDialog((SetReplyIconCell) view);
                } else if (position == clearRow) {
                    selectedColor = -1;
                    selectedEmoji = 0;
                    selectedEmojiCollectible = null;
                    selectedPeerCollectible = null;
                    selectedResaleGift = null;
                    updateMessages();
                    if (type == PAGE_PROFILE) {
                        namePage.updateMessages();
                    }
                    if (setReplyIconCell != null) {
                        setReplyIconCell.update(true);
                    }
                    updateProfilePreview(true);
                    updateButton(true);
                    if (profilePage != null && profilePage.profilePreview != null && namePage != null) {
                        profilePage.profilePreview.overrideAvatarColor(namePage.selectedColor);
                    }
                } else if (position >= giftsStartRow && position < giftsEndRow) {
                    final int index = position - giftsStartRow;
                    if (selectedTabGift == null) {
                        if (index < 0 || index >= uniqueGifts.size()) return;
                        final TL_stars.TL_starGiftUnique gift = uniqueGifts.get(index);
                        if (type == PAGE_NAME) {
                            if (!(gift.peer_color instanceof TLRPC.TL_peerColorCollectible)) return;
                            selectedEmoji = 0;
                            selectedColor = -1;
                            selectedResaleGift = null;
                            selectedEmojiCollectible = null;
                            selectedPeerCollectible = (TLRPC.TL_peerColorCollectible) gift.peer_color;
                        } else {
                            selectedEmoji = 0;
                            selectedColor = -1;
                            selectedResaleGift = null;
                            selectedEmojiCollectible = MessagesController.emojiStatusCollectibleFromGift(gift);
                            selectedPeerCollectible = null;
                        }
                        updateProfilePreview(true);
                        updateMessages();
                        updateButton(true);
                        if (setReplyIconCell != null) {
                            setReplyIconCell.update(true);
                        }
                    } else if (resaleGifts != null) {
                        if (index < 0 || index >= uniqueGifts.size()) return;
                        final TL_stars.TL_starGiftUnique gift = uniqueGifts.get(index);
                        if (type == PAGE_NAME) {
                            if (!(gift.peer_color instanceof TLRPC.TL_peerColorCollectible)) return;
                            selectedEmoji = 0;
                            selectedColor = -1;
                            selectedEmojiCollectible = null;
                            selectedPeerCollectible = (TLRPC.TL_peerColorCollectible) gift.peer_color;
                        } else {
                            selectedEmoji = 0;
                            selectedColor = -1;
                            selectedEmojiCollectible = MessagesController.emojiStatusCollectibleFromGift(gift);
                            selectedPeerCollectible = null;
                        }
                        selectedResaleGift = gift;
                        updateProfilePreview(true);
                        updateMessages();
                        updateButton(true);
                        if (setReplyIconCell != null) {
                            setReplyIconCell.update(true);
                        }
                    }
                }
            });
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (selectedTabGift != null) {
                        if (resaleGifts != null && seesLoading()) {
                            resaleGifts.load();
                        }
                    } else {
                        final StarsController.GiftsList giftsList = type == PAGE_NAME ? giftsWithPeerColor : gifts;
                        if (giftsList != null && seesLoading()) {
                            giftsList.load();
                        }
                    }
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            buttonContainer = new FrameLayout(getContext());
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

            buttonShadow = new View(getContext());
            buttonShadow.setBackgroundColor(getThemedColor(Theme.key_divider));
            buttonShadow.setAlpha(0.0f);
            buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, .66f, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));

            SpannableStringBuilder buttonLock = new SpannableStringBuilder("l");
            buttonLock.setSpan(new ColoredImageSpan(R.drawable.msg_mini_lock2), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            buttonUnlocked = getString(isChannel ? R.string.ChannelColorApply : R.string.UserColorApply);
            buttonLocked = new SpannableStringBuilder(buttonLock).append(" ").append(buttonUnlocked);
            buttonCollectible = getString(R.string.UserColorApplyCollectible);

            button = new ButtonWithCounterView(getContext(), getResourceProvider());
            button.text.setHacks(true, true, true);
            button.setText(isChannel ? buttonUnlocked : (!getUserConfig().isPremium() ? buttonLocked : (selectedEmojiCollectible != null ? buttonCollectible : buttonUnlocked)), false);
            button.setOnClickListener(v -> buttonClick());
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 14, 14.66f, 14, 14));

            addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateButtonY();
                }
            });
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            listView.setItemAnimator(itemAnimator);

            if (type == PAGE_PROFILE) {
                profilePreview = new ProfilePreview(getContext(), currentAccount, dialogId, resourceProvider);
                updateProfilePreview(false);
                addView(profilePreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            } else {
                messagesCellPreview = new ThemePreviewMessagesCell(getContext(), parentLayout, ThemePreviewMessagesCell.TYPE_PEER_COLOR, dialogId, resourceProvider);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    messagesCellPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
                messagesCellPreview.fragment = PeerColorActivity.this;
                addView(messagesCellPreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            }

            updateColors();
            updateRows();

            setWillNotDraw(false);
        }

        private int actionBarHeight;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (getParentLayout() != null) {
                getParentLayout().drawHeaderShadow(canvas, actionBarHeight);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (type == PAGE_NAME) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                actionBarHeight = messagesCellPreview.getMeasuredHeight() + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                ((MarginLayoutParams) messagesCellPreview.getLayoutParams()).topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                ((MarginLayoutParams) listView.getLayoutParams()).topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                listView.setPadding(0, messagesCellPreview.getMeasuredHeight(), 0, 0);
            } else {
                actionBarHeight = dp(230) + AndroidUtilities.statusBarHeight;
                ((MarginLayoutParams) listView.getLayoutParams()).topMargin = actionBarHeight;
                ((MarginLayoutParams) profilePreview.getLayoutParams()).height = actionBarHeight;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public boolean hasUnsavedChanged() {
            if (isChannel) {
                final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                if (chat == null) return false;
                if (type == PAGE_NAME) {
                    return !(selectedColor == ChatObject.getColorId(chat) && selectedEmoji == ChatObject.getEmojiId(chat) && eq(chat != null && chat.color instanceof TLRPC.TL_peerColorCollectible ? (TLRPC.TL_peerColorCollectible) chat.color : null, selectedPeerCollectible));
                } else {
                    return !(selectedColor == (chat != null && chat.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? -1 : ChatObject.getProfileColorId(chat)) && selectedEmoji == (chat != null && chat.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? 0 : ChatObject.getOnlyProfileEmojiId(chat)) && eq(chat == null ? null : chat.emoji_status, selectedEmojiCollectible));
                }
            } else {
                final TLRPC.User me = getUserConfig().getCurrentUser();
                if (me == null) return false;
                if (type == PAGE_NAME) {
                    return !(selectedColor == (me != null && me.color instanceof TLRPC.TL_peerColorCollectible ? -1 : UserObject.getColorId(me)) && selectedEmoji == UserObject.getEmojiId(me) && eq(me != null && me.color instanceof TLRPC.TL_peerColorCollectible ? (TLRPC.TL_peerColorCollectible) me.color : null, selectedPeerCollectible));
                } else {
                    return !(selectedColor == (me != null && me.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? -1 : UserObject.getProfileColorId(me)) && selectedEmoji == (me != null && me.emoji_status instanceof TLRPC.TL_emojiStatusCollectible ? 0 : UserObject.getOnlyProfileEmojiId(me)) && eq(me == null ? null : me.emoji_status, selectedEmojiCollectible));
                }
            }
        }

        private void updateButtonY() {
            if (buttonContainer == null) {
                return;
            }
            final int lastPosition = listAdapter.getItemCount() - 1;
            boolean foundLastPosition = false;
            int maxTop = 0;
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                final int position = listView.getChildAdapterPosition(child);
                if (position != RecyclerListView.NO_POSITION && position <= lastPosition) {
                    maxTop = Math.max(maxTop, child.getTop());
                    if (position == lastPosition) {
                        foundLastPosition = true;
                    }
                }
            }
            if (!foundLastPosition) {
                maxTop = listView.getMeasuredHeight();
            }
            float buttonTy = Math.max(0, maxTop - (listView.getMeasuredHeight() - dp(14 + 48 + 14 + .66f)));
            if (type == PAGE_PROFILE || type == PAGE_NAME) {
                buttonShadow.animate().alpha(buttonTy > 0 ? 0.0f : 1.0f).start();
                buttonTy = 0.0f;
            }
            buttonContainer.setTranslationY(buttonTy);
        }


        private class SetReplyIconCell extends FrameLayout {

            private TextView textView;
            private Text offText;
            private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;

            public SetReplyIconCell(Context context) {
                super(context);

                setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                if (type == PAGE_NAME) {
                    textView.setText(getString(isChannel ? R.string.ChannelReplyIcon : R.string.UserReplyIcon));
                } else {
                    textView.setText(getString(isChannel ? R.string.ChannelProfileIcon : R.string.UserProfileIcon));
                }
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 20, 0));

                imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            }

            public void updateColors() {
                textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            }

            public void update(boolean animated) {
                if (selectedEmoji != 0) {
                    imageDrawable.set(selectedEmoji, animated);
                    offText = null;
                } else {
                    imageDrawable.set((Drawable) null, animated);
                    if (offText == null) {
                        offText = new Text(getString(isChannel ? R.string.ChannelReplyIconOff : R.string.UserReplyIconOff), 16);
                    }
                }
            }

            public void updateImageBounds() {
                imageDrawable.setBounds(
                    LocaleController.isRTL ? dp(21) : getWidth() - imageDrawable.getIntrinsicWidth() - dp(21),
                    (getHeight() - imageDrawable.getIntrinsicHeight()) / 2,
                    LocaleController.isRTL ? dp(21) + imageDrawable.getIntrinsicWidth() : getWidth() - dp(21),
                    (getHeight() + imageDrawable.getIntrinsicHeight()) / 2
                );
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                updateImageBounds();
                imageDrawable.setColor(getColor());
                if (offText != null) {
                    offText.draw(canvas, getMeasuredWidth() - offText.getWidth() - dp(19), getMeasuredHeight() / 2f, getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 1f);
                } else {
                    imageDrawable.draw(canvas);
                }
            }

            public int getColor() {
                if (selectedColor < 0) {
                    if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider);
                    } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                        return Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle, resourceProvider), .5f);
                    } else {
                        return Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider), Theme.multAlpha(adaptProfileEmojiColor(Theme.getColor(Theme.key_actionBarDefault, resourceProvider)), .7f));
                    }
                } else if (selectedColor < 7) {
                    return getThemedColor(Theme.keys_avatar_nameInMessage[selectedColor]);
                } else {
                    MessagesController.PeerColors peerColors = type == PAGE_NAME ? MessagesController.getInstance(currentAccount).peerColors : MessagesController.getInstance(currentAccount).profilePeerColors;
                    if (peerColors != null) {
                        MessagesController.PeerColor color = peerColors.getColor(selectedColor);
                        if (color != null) {
                            return color.getColor1();
                        }
                    }
                }
                return getThemedColor(Theme.keys_avatar_nameInMessage[0]);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY)
                );
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                imageDrawable.detach();
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                imageDrawable.attach();
            }
        }

        private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
        public void showSelectStatusDialog(SetReplyIconCell cell) {
            if (selectAnimatedEmojiDialog != null || cell == null) {
                return;
            }
            final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
            int xoff = 0, yoff = 0;

            AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
            View scrimDrawableParent = null;
            final int popupHeight = (int) Math.min(AndroidUtilities.dp(410 - 16 - 64), AndroidUtilities.displaySize.y * .75f);
            final int popupWidth = (int) Math.min(dp(340 - 16), AndroidUtilities.displaySize.x * .95f);
            if (cell != null) {
                scrimDrawable = cell.imageDrawable;
                scrimDrawableParent = cell;
                if (cell.imageDrawable != null) {
                    cell.imageDrawable.play();
                    cell.updateImageBounds();
                    AndroidUtilities.rectTmp2.set(cell.imageDrawable.getBounds());
                    if (type == PAGE_NAME) {
                        yoff = -AndroidUtilities.rectTmp2.centerY() + dp(12) - popupHeight;
                    } else {
                        yoff = -(cell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
                    }
                    xoff = AndroidUtilities.rectTmp2.centerX() - (AndroidUtilities.displaySize.x - popupWidth);
                }
            }
            SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(PeerColorActivity.this, getContext(), true, xoff, type == PAGE_NAME ? SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON : SelectAnimatedEmojiDialog.TYPE_SET_REPLY_ICON_BOTTOM, true, getResourceProvider(), type == PAGE_NAME ? 24 : 16, cell.getColor()) {
                @Override
                protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                    if (gift != null) {
                        if (type == PAGE_PROFILE) {
                            if (!(gift.peer_color instanceof TLRPC.TL_peerColorCollectible)) return;
                            selectedPeerCollectible = (TLRPC.TL_peerColorCollectible) gift.peer_color;
                            selectedEmojiCollectible = null;
                        } else {
                            selectedPeerCollectible = null;
                            selectedEmojiCollectible = MessagesController.emojiStatusCollectibleFromGift(gift);
                        }
                        selectedResaleGift = null;
                        selectedColor = -1;
                    } else {
                        selectedEmoji = documentId == null ? 0 : documentId;
                        selectedEmojiCollectible = null;
                        selectedPeerCollectible = null;
                        selectedResaleGift = null;
                    }
                    if (cell != null) {
                        cell.update(true);
                    }
                    updateProfilePreview(true);
                    updateMessages();
                    updateButton(true);
                    if (popup[0] != null) {
                        selectAnimatedEmojiDialog = null;
                        popup[0].dismiss();
                    }
                }

                @Override
                protected float getScrimDrawableTranslationY() {
                    return 0;
                }
            };
            popupLayout.useAccentForPlus = true;
            popupLayout.setSelected(selectedEmoji == 0 ? null : selectedEmoji);
            popupLayout.setSaveState(3);
            popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
            popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    selectAnimatedEmojiDialog = null;
                }
            };
            popup[0].showAsDropDown(cell, 0, yoff, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT));
            popup[0].dimBehind();
        }

        public void checkResetColorButton() {
            if (type != PAGE_PROFILE) {
                return;
            }
            final int wasIndex = clearRow;
            updateRows();
            if (wasIndex >= 0 && clearRow < 0) {
                listAdapter.notifyItemRangeRemoved(wasIndex, 2);
            } else if (wasIndex < 0 && clearRow >= 0) {
                listAdapter.notifyItemRangeInserted(clearRow, 2);
            }
        }

        public void updateSelectedGift() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                final View child = listView.getChildAt(i);
                if (child instanceof GiftCell) {
                    final GiftCell cell = (GiftCell) child;
                    cell.setSelected(
                        selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == cell.getGiftId() ||
                        selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == cell.getGiftId(),
                        true
                    );
                } else if (child instanceof GiftSheet.GiftCell) {
                    final GiftSheet.GiftCell cell = (GiftSheet.GiftCell) child;
                    cell.setSelected(
                        selectedEmojiCollectible != null && selectedEmojiCollectible.collectible_id == cell.getGiftId() ||
                        selectedPeerCollectible != null && selectedPeerCollectible.collectible_id == cell.getGiftId(),
                        true
                    );
                }
            }
        }

        private void updateRows() {
            clearRow = -1;
            shadowRow = -1;
            giftsHeaderRow = -1;
            giftsStartRow = -1;
            giftsLoadingStartRow = -1;
            giftsLoadingEndRow = -1;
            giftsEndRow = -1;
            giftsInfoRow = -1;
            giftsTabsRow = -1;
            giftsEmptyRow = -1;
            giftsCount = 0;
            uniqueGifts.clear();

            rowCount = 0;
//            if (type == PAGE_NAME) {
//                previewRow = rowCount++;
//            }
            colorPickerRow = rowCount++;
            iconRow = rowCount++;
            infoRow = rowCount++;
            if ((type == PAGE_PROFILE) && (selectedColor >= 0 || selectedEmojiCollectible != null || selectedPeerCollectible != null)) {
                clearRow = rowCount++;
                shadowRow = rowCount++;
            }
            final StarsController.GiftsList giftsList = type == PAGE_NAME ? giftsWithPeerColor : gifts;
            if ((type == PAGE_PROFILE || type == PAGE_NAME) && giftsList != null) {
                giftsTabsRow = rowCount++;
                if (selectedTabGift == null) {
                    for (int i = 0; i < giftsList.gifts.size(); ++i) {
                        TL_stars.SavedStarGift savedGift = giftsList.gifts.get(i);
                        if (savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                            uniqueGifts.add((TL_stars.TL_starGiftUnique) savedGift.gift);
                        }
                    }
                    giftsStartRow = rowCount;
                    rowCount += uniqueGifts.size();
                    giftsCount += uniqueGifts.size();
                    giftsEndRow = rowCount;
                    if (gifts.loading || !gifts.endReached) {
                        giftsLoadingStartRow = rowCount;
                        final int spanCountLeft = 3 - (giftsCount % 3);
                        final int loadingCells = giftsCount <= 0 ? 9 : spanCountLeft <= 0 ? 3 : spanCountLeft;
                        rowCount += loadingCells;
                        giftsCount += loadingCells;
                        giftsLoadingEndRow = rowCount;
                    } else if (uniqueGifts.isEmpty()) {
                        giftsEmptyRow = rowCount++;
                    }
                    if (giftsList != null && seesLoading()) {
                        giftsList.load();
                    }
                } else if (selectedTabGift != null && resaleGifts != null) {
                    final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                    for (int i = 0; i < resaleGifts.gifts.size(); ++i) {
                        TL_stars.TL_starGiftUnique g = resaleGifts.gifts.get(i);
                        if (DialogObject.getPeerDialogId(g.owner_id) != selfId && DialogObject.getPeerDialogId(g.host_id) != selfId) {
                            uniqueGifts.add(g);
                        }
                    }
                    giftsStartRow = rowCount;
                    rowCount += uniqueGifts.size();
                    giftsCount += uniqueGifts.size();
                    giftsEndRow = rowCount;
                    if (resaleGifts.loading || !resaleGifts.endReached) {
                        giftsLoadingStartRow = rowCount;
                        final int spanCountLeft = 3 - (giftsCount % 3);
                        final int loadingCells = giftsCount <= 0 ? 9 : spanCountLeft <= 0 ? 3 : spanCountLeft;
                        rowCount += loadingCells;
                        giftsCount += loadingCells;
                        giftsLoadingEndRow = rowCount;
                    }
                    if (resaleGifts != null && seesLoading()) {
                        resaleGifts.load();
                    }
                }
                giftsInfoRow = rowCount++;
            }
            buttonRow = rowCount++;
        }

        public boolean seesLoading() {
            if (listView == null) return false;
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof FlickerLoadingView) {
                    return true;
                }
            }
            return false;
        }

        public void updateButton(boolean animated) {
            if (button == null) return;
            if (selectedResaleGift != null) {
                final TL_stars.TL_starGiftUnique gift = selectedResaleGift;
                final AmountUtils.Amount stars = gift.getResellAmount(AmountUtils.Currency.STARS);
                if (gift.resale_ton_only) {
                    final AmountUtils.Amount ton = gift.getResellAmount(AmountUtils.Currency.TON);
                    button.setText(StarsIntroActivity.replaceStars(true, LocaleController.formatString(R.string.ResellGiftBuyTON, ton.asFormatString())), animated);
                    button.setSubText(StarsIntroActivity.replaceStars(formatPluralStringComma("ResellGiftBuyEq", (int) stars.asDecimal())), animated);
                } else {
                    button.setText(StarsIntroActivity.replaceStars(formatPluralStringComma("ResellGiftBuy", (int) stars.asDecimal())), animated);
                    button.setSubText(null, animated);
                }
            } else {
                button.setText(!getUserConfig().isPremium() && !isChannel ? buttonLocked : (selectedEmojiCollectible != null ? buttonCollectible : buttonUnlocked), animated);
                button.setSubText(null, animated);
            }
        }

        public void updateProfilePreview(boolean animated) {
            if (peerColorPicker != null) {
                peerColorPicker.setSelected(selectedColor, animated);
            }
            if (profilePreview != null) {
                if (selectedEmojiCollectible != null) {
                    profilePreview.setStatusEmoji(selectedEmojiCollectible.document_id, true, animated);
                    profilePreview.setColor(MessagesController.PeerColor.fromCollectible(selectedEmojiCollectible), animated);
                    profilePreview.setEmoji(selectedEmojiCollectible.pattern_document_id, true, animated);
                } else {
                    if (DialogObject.isEmojiStatusCollectible(dialogId)) {
                        profilePreview.setStatusEmoji(0, false, animated);
                    } else {
                        profilePreview.setStatusEmoji(DialogObject.getEmojiStatusDocumentId(dialogId), DialogObject.isEmojiStatusCollectible(dialogId), animated);
                    }
                    profilePreview.setColor(selectedColor, animated);
                    profilePreview.setEmoji(selectedEmoji, false, animated);
                }
            }
            if (type == PAGE_PROFILE && colorBar != null) {
                if (selectedEmojiCollectible != null) {
                    colorBar.setColor(MessagesController.PeerColor.fromCollectible(selectedEmojiCollectible), animated);
                } else {
                    colorBar.setColor(currentAccount, selectedColor, animated);
                }
            }
            checkResetColorButton();
            updateSelectedGift();
        }

        private void updateMessages() {
            if (messagesCellPreview != null) {
                ChatMessageCell[] cells = messagesCellPreview.getCells();
                for (int i = 0; i < cells.length; ++i) {
                    if (cells[i] != null) {
                        MessageObject msg = cells[i].getMessageObject();
                        if (msg != null) {
                            msg.notime = true;
                            if (peerColorPicker != null) {
                                msg.overrideLinkColor = peerColorPicker.getColorId();
                            }
                            msg.overrideLinkEmoji = selectedEmoji;
                            msg.overrideLinkPeerColor = selectedPeerCollectible;
                            cells[i].setAvatar(msg);
                            cells[i].invalidate();
                        }
                    }
                }
            }
        }

        public void update() {
            updateRows();
            listAdapter.notifyDataSetChanged();
        }

        public void updateColors() {
            listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            if (button != null) {
                button.updateColors();
            }
            if (messagesCellPreview != null) {
                messagesCellPreview.invalidate();
            }
            updateProfilePreview(true);
            buttonContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            buttonShadow.setBackgroundColor(getThemedColor(Theme.key_divider));
            AndroidUtilities.forEachViews(listView, view -> {
                if (view instanceof PeerColorGrid) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((PeerColorGrid) view).updateColors();
                } else if (view instanceof TextCell) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((TextCell) view).updateColors();
                } else if (view instanceof SetReplyIconCell) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((SetReplyIconCell) view).updateColors();
                } else if (view instanceof HeaderCell) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                } else if (view instanceof GiftCell) {
                    ((GiftCell) view).card.invalidate();
                } else if (view instanceof GiftSheet.Tabs) {
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    ((GiftSheet.Tabs) view).updateColors();
                }
            });
        }

        public void premiumChanged() {
            updateButton(true);
        }
    }

    private Theme.ResourcesProvider parentResourcesProvider;
    private final SparseIntArray currentColors = new SparseIntArray();
    private final Theme.MessageDrawable msgInDrawable, msgInDrawableSelected;

    public void updateThemeColors() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }

        if (isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }

        currentColors.clear();
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        for (int i = 0; i < themeColors.size(); ++i) {
            currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
        }
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (accent != null) {
            accent.fillAccentColors(themeColors, currentColors);
        }

        if (namePage != null && namePage.messagesCellPreview != null) {
            Theme.BackgroundDrawableSettings bg = Theme.createBackgroundDrawable(themeInfo, currentColors, wallpaperLink[0], 0, true);
            namePage.messagesCellPreview.setOverrideBackground(bg.themedWallpaper != null ? bg.themedWallpaper : bg.wallpaper);
        }
    }

    public PeerColorActivity(long dialogId) {
        super();

        this.dialogId = dialogId;
        this.isChannel = dialogId != 0;
        if (dialogId >= 0) {
            StarsController.getInstance(currentAccount).loadStarGifts();

            this.gifts = new StarsController.GiftsList(currentAccount, dialogId, false);
            this.gifts.forceTypeIncludeFlag(StarsController.GiftsList.INCLUDE_TYPE_UNIQUE_FLAG, false);
            this.gifts.load();

            this.giftsWithPeerColor = new StarsController.GiftsList(currentAccount, dialogId, false);
            this.giftsWithPeerColor.forceTypeIncludeFlag(StarsController.GiftsList.INCLUDE_TYPE_UNIQUE_FLAG, false);
            this.giftsWithPeerColor.peer_color_available = true;
            this.giftsWithPeerColor.load();
        } else {
            this.gifts = null;
            this.giftsWithPeerColor = null;
        }

        resourceProvider = new Theme.ResourcesProvider() {
            @Override
            public int getColor(int key) {
                int index = currentColors.indexOfKey(key);
                if (index >= 0) {
                    return currentColors.valueAt(index);
                }
                if (parentResourcesProvider != null) {
                    return parentResourcesProvider.getColor(key);
                }
                return Theme.getColor(key);
            }

            @Override
            public Drawable getDrawable(String drawableKey) {
                if (drawableKey.equals(Theme.key_drawable_msgIn)) {
                    return msgInDrawable;
                }
                if (drawableKey.equals(Theme.key_drawable_msgInSelected)) {
                    return msgInDrawableSelected;
                }
                if (parentResourcesProvider != null) {
                    return parentResourcesProvider.getDrawable(drawableKey);
                }
                return Theme.getThemeDrawable(drawableKey);
            }

            @Override
            public Paint getPaint(String paintKey) {
                return Theme.ResourcesProvider.super.getPaint(paintKey);
            }

            @Override
            public boolean isDark() {
                return isDark;
            }
        };
        msgInDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false, resourceProvider);
        msgInDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, true, resourceProvider);
    }

    @Override
    public void setResourceProvider(Theme.ResourcesProvider resourceProvider) {
        parentResourcesProvider = resourceProvider;
    }

    private boolean startAtProfile;
    public PeerColorActivity startOnProfile() {
        this.startAtProfile = true;
        return this;
    }

    private BaseFragment bulletinFragment;
    public PeerColorActivity setOnApplied(BaseFragment bulletinFragment) {
        this.bulletinFragment = bulletinFragment;
        return this;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.starUserGiftsLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.starGiftsLoaded);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return dp(62);
            }

            @Override
            public boolean clipWithGradient(int tag) {
                return true;
            }
        });
        getMediaDataController().loadReplyIcons();
        if (MessagesController.getInstance(currentAccount).peerColors == null && BuildVars.DEBUG_PRIVATE_VERSION) {
            MessagesController.getInstance(currentAccount).loadAppConfig(true);
        }
        return super.onFragmentCreate();
    }

    private ViewPagerFixed viewPager;

    private ImageView backButton;
    private ImageView dayNightItem;

    private FrameLayout actionBarContainer;
    private FilledTabsView tabsView;
    private SimpleTextView titleView;

    @Override
    public View createView(Context context) {

        namePage = new Page(context, PAGE_NAME);
        profilePage = new Page(context, PAGE_PROFILE);

        actionBar.setCastShadows(false);
        actionBar.setVisibility(View.GONE);
        actionBar.setAllowOverlayTitle(false);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (actionBarContainer != null) {
                    ((MarginLayoutParams) actionBarContainer.getLayoutParams()).height = ActionBar.getCurrentActionBarHeight();
                    ((MarginLayoutParams) actionBarContainer.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        frameLayout.setFitsSystemWindows(true);

        colorBar = new ColoredActionBar(context, resourceProvider) {
            @Override
            protected void onUpdateColor() {
                updateLightStatusBar();
                updateActionBarButtonsColor();
                if (tabsView != null) {
                    tabsView.setBackgroundColor(getTabsViewBackgroundColor());
                }
            }

            private int lastBtnColor = 0;
            public void updateActionBarButtonsColor() {
                final int btnColor = getActionBarButtonColor();
                if (lastBtnColor != btnColor) {
                    if (backButton != null) {
                        lastBtnColor = btnColor;
                        backButton.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                    if (dayNightItem != null) {
                        lastBtnColor = btnColor;
                        dayNightItem.setColorFilter(new PorterDuffColorFilter(btnColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        };
        profilePage.updateProfilePreview(false);
        frameLayout.addView(colorBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                tabsView.setSelected(viewPager.getPositionAnimated());
                colorBar.setProgressToGradient(1 - viewPager.getPositionAnimated());
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }

            @Override
            public View createView(int viewType) {
                if (viewType == PAGE_NAME) return namePage;
                if (viewType == PAGE_PROFILE) return profilePage;
                return null;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {

            }
        });
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        actionBarContainer = new FrameLayout(context);
        frameLayout.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        if (!isChannel) {
            tabsView = new FilledTabsView(context);
            tabsView.setTabs(
                getString(isChannel ? R.string.ChannelColorTabProfile : R.string.UserColorTabProfile),
                getString(isChannel ? R.string.ChannelColorTabName : R.string.UserColorTabName)
            );
            tabsView.onTabSelected(tab -> {
                if (viewPager != null) {
                    viewPager.scrollToPosition(tab);
                }
            });
            actionBarContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER));
        } else {
            titleView = new SimpleTextView(context);
            titleView.setText(getString(R.string.ChannelColorTitle2));
            titleView.setEllipsizeByGradient(true);
            titleView.setTextSize(20);
            titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            titleView.setTypeface(AndroidUtilities.bold());
            actionBarContainer.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 72, 0, 72, 0));
        }

//        if (startAtProfile) {
//            viewPager.setPosition(1);
//            if (tabsView != null) {
//                tabsView.setSelected(1);
//            }
//        }
        if (colorBar != null) {
            colorBar.setProgressToGradient(1f);
            updateLightStatusBar();
        }

        backButton = new ImageView(context);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        backButton.setOnClickListener(v -> {
            if (onBackPressed()) {
                finishFragment();
            }
        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(54, 54, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, dp(28), dp(28), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        if (!isDark) {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(0);
        } else {
            sunDrawable.setCurrentFrame(35);
            sunDrawable.setCustomEndFrame(36);
        }
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        sunDrawable.commitApplyLayerColors();

        dayNightItem = new ImageView(context);
        dayNightItem.setScaleType(ImageView.ScaleType.CENTER);
        dayNightItem.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarWhiteSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        dayNightItem.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        dayNightItem.setOnClickListener(v -> {
            toggleTheme();
        });
        actionBarContainer.addView(dayNightItem, LayoutHelper.createFrame(54, 54, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        dayNightItem.setImageDrawable(sunDrawable);

        colorBar.updateColors();

        fragmentView = contentView = frameLayout;

        return contentView;
    }

    private boolean isDark = Theme.isCurrentThemeDark();
    private RLottieDrawable sunDrawable;

    public boolean hasUnsavedChanged() {
        return namePage.hasUnsavedChanged() || profilePage.hasUnsavedChanged();
    }

    private void setLoading(boolean loading) {
        if (namePage != null && namePage.button != null) {
            namePage.button.setLoading(loading);
        }
        if (profilePage != null && profilePage.button != null) {
            profilePage.button.setLoading(loading);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            showUnsavedAlert();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (!isChannel && hasUnsavedChanged() && getUserConfig().isPremium()) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }

    private void showUnsavedAlert() {
        if (getVisibleDialog() != null) {
            return;
        }
        AlertDialog alertDialog = new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(getString(isChannel ? R.string.ChannelColorUnsaved : R.string.UserColorUnsaved))
            .setMessage(getString(isChannel ? R.string.ChannelColorUnsavedMessage : R.string.UserColorUnsavedMessage))
            .setNegativeButton(getString(R.string.Dismiss), (di, w) -> {
                finishFragment();
            })
            .setPositiveButton(getString(R.string.ApplyTheme), (di, w) -> {
                buttonClick();
            })
            .create();
        showDialog(alertDialog);
        ((TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)).setTextColor(getThemedColor(Theme.key_text_RedBold));
    }

    private void buttonClick() {
        if (loading) {
            return;
        }
        if (isChannel) {
            finishFragment();
        } else {
            if (!getUserConfig().isPremium()) {
                showDialog(new PremiumFeatureBottomSheet(PeerColorActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_NAME_COLOR, true));
                return;
            }
        }

        final Page page = viewPager.getCurrentPosition() == PAGE_NAME ? namePage : profilePage;
        if (page.selectedResaleGift != null) {
            final Page otherPage = viewPager.getCurrentPosition() == PAGE_NAME ? profilePage : namePage;
            otherPage.setupValues();

            loading = true;
            page.button.setLoading(true);
            buy(page.selectedResaleGift, bought -> {
                loading = false;
                page.button.setLoading(false);
                if (bought) {
                    apply();
                    finishFragment();
                    showBulletin();
                }
            });
            return;
        } else {
            final Page otherPage = viewPager.getCurrentPosition() == PAGE_NAME ? profilePage : namePage;
            if (otherPage.selectedResaleGift != null) {
                otherPage.setupValues();
            }
        }

        apply();
        finishFragment();
        showBulletin();
    }

    public void buy(TL_stars.TL_starGiftUnique gift, Utilities.Callback<Boolean> bought) {
        final long to = UserConfig.getInstance(currentAccount).getClientUserId();
        final AmountUtils.Currency currency = gift.resale_ton_only ?
                AmountUtils.Currency.TON : AmountUtils.Currency.STARS;
        StarsController.getInstance(currentAccount, currency).getResellingGiftForm(gift, to, form -> {
            if (form == null) return;
            final StarGiftSheet.PaymentFormState initial = new StarGiftSheet.PaymentFormState(currency, form);
            final String giftName = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');
            final boolean[] buying = new boolean[1];
            final StarGiftSheet.ResaleBuyTransferAlert sheet = new StarGiftSheet.ResaleBuyTransferAlert(getContext(), resourceProvider, gift, initial, currentAccount, to, giftName, (state, progress) -> {
                buying[0] = true;
                progress.init();
                StarsController.getInstance(currentAccount, state.currency).buyResellingGift(state.form, gift, to, (status, err) -> {
                    progress.end();
                    if (bought != null) {
                        bought.run(status);
                    }
                });
            });
            sheet.alertDialog.setOnDismissListener(di -> {
                if (buying[0]) return;
                if (bought != null) {
                    bought.run(false);
                }
            });
            sheet.show();
        });
    }

    private boolean applyingName, applyingProfile;
    private boolean applying;
    private void apply() {
        if (applying || !isChannel && !getUserConfig().isPremium()) {
            return;
        }

        if (isChannel) {
            finishFragment();
        } else {
            final TLRPC.User me = getUserConfig().getCurrentUser();
            if (me.color == null) {
                me.color = new TLRPC.TL_peerColor();
                me.color.flags |= 1;
                me.color.color = (int) (me.id % 7);
            }
            if (
                namePage.selectedColor != UserObject.getColorId(me) ||
                namePage.selectedEmoji != UserObject.getEmojiId(me) ||
                (namePage.selectedPeerCollectible == null ? 0 : namePage.selectedPeerCollectible.collectible_id) != (me.color instanceof TLRPC.TL_peerColorCollectible ? me.color.collectible_id : 0)
            ) {
                applyingName = true;
                final TL_account.updateColor req = new TL_account.updateColor();
                me.flags2 |= 256;
                me.color.flags |= 1;
                if (namePage.selectedPeerCollectible != null) {
                    req.flags |= 4;
                    req.color = new TLRPC.TL_inputPeerColorCollectible();
                    req.color.collectible_id = namePage.selectedPeerCollectible.collectible_id;
                    me.color = namePage.selectedPeerCollectible;
                } else {
                    req.flags |= 4;
                    req.color = new TLRPC.TL_peerColor();
                    req.color.flags |= 1;
                    req.color.color = namePage.selectedColor;
                    me.color.flags |= 1;
                    me.color.color = namePage.selectedColor;
                    if (namePage.selectedEmoji != 0) {
                        req.flags |= 1;
                        me.color.flags |= 2;
                        req.color.flags |= 2;
                        req.color.background_emoji_id = me.color.background_emoji_id = namePage.selectedEmoji;
                    } else {
                        me.color.flags &= ~2;
                        me.color.background_emoji_id = 0;
                    }
                }
                getConnectionsManager().sendRequest(req, null);
            }
            if (
                profilePage.selectedColor != UserObject.getProfileColorId(me) ||
                profilePage.selectedEmoji != UserObject.getOnlyProfileEmojiId(me) ||
                (profilePage.selectedEmojiCollectible == null ? 0 : profilePage.selectedEmojiCollectible.collectible_id) != UserObject.getProfileCollectibleId(me)
            ) {
                applyingProfile = true;
                if (me.profile_color == null) {
                    me.profile_color = new TLRPC.TL_peerColor();
                }
                TL_account.updateColor req = new TL_account.updateColor();
                req.for_profile = true;
                me.flags2 |= 512;
                if (profilePage.selectedColor < 0) {
                    me.profile_color.flags &=~ 1;
                } else {
                    if (req.color == null) {
                        req.flags |= 4;
                        req.color = new TLRPC.TL_peerColor();
                    }
                    req.color.flags |= 1;
                    req.color.color = profilePage.selectedColor;
                    me.profile_color.flags |= 1;
                    me.profile_color.color = profilePage.selectedColor;
                }
                if (profilePage.selectedEmoji != 0) {
                    req.flags |= 1;
                    me.profile_color.flags |= 2;
                    if (req.color == null) {
                        req.flags |= 4;
                        req.color = new TLRPC.TL_peerColor();
                    }
                    req.color.flags |= 2;
                    req.color.background_emoji_id = me.profile_color.background_emoji_id = profilePage.selectedEmoji;
                } else {
                    me.profile_color.flags &=~ 2;
                    me.profile_color.background_emoji_id = 0;
                }
                getConnectionsManager().sendRequest(req, null);
            }
            if (!eq(me.emoji_status, profilePage.selectedEmojiCollectible) && (profilePage.selectedEmojiCollectible != null || DialogObject.isEmojiStatusCollectible(me.emoji_status))) {
                TLRPC.EmojiStatus new_emoji_status = new TLRPC.TL_emojiStatusEmpty();
                TL_stars.TL_starGiftUnique gift = null;
                if (profilePage.selectedEmojiCollectible != null) {
                    final long id = profilePage.selectedEmojiCollectible.collectible_id;
                    for (int i = 0; i < profilePage.uniqueGifts.size(); ++i) {
                        final TL_stars.TL_starGiftUnique g = profilePage.uniqueGifts.get(i);
                        if (g.id == id) {
                            gift = g;
                            break;
                        }
                    }
                }
                if (gift != null) {
                    TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                    status.collectible_id = gift.id;
                    new_emoji_status = status;
                }
                getMessagesController().updateEmojiStatus(0, new_emoji_status, gift);
            }
            getMessagesController().putUser(me, false);
            getUserConfig().saveConfig(true);
            finishFragment();
            showBulletin();
        }
        applying = true;
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
    }

    private void showBulletin() {
        if (bulletinFragment != null) {
            if (applyingName && (!applyingProfile || getCurrentPage() == namePage)) {
                if (namePage.selectedColor < 0) {
                    if (namePage.selectedPeerCollectible == null) return;
                    BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                        PeerColorDrawable.from(namePage.selectedPeerCollectible),
                        getString(isChannel ? R.string.ChannelColorApplied : R.string.UserColorApplied)
                    ).show();
                } else {
                    BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                        PeerColorDrawable.from(currentAccount, namePage.selectedColor),
                        getString(isChannel ? R.string.ChannelColorApplied : R.string.UserColorApplied)
                    ).show();
                }
            } else if (applyingProfile && (!applyingName || getCurrentPage() == profilePage)) {
                if (profilePage.selectedColor < 0) {
                    if (profilePage.selectedEmoji != 0) {
                        BulletinFactory.of(bulletinFragment).createStaticEmojiBulletin(
                            AnimatedEmojiDrawable.findDocument(currentAccount, profilePage.selectedEmoji),
                            getString(isChannel ? R.string.ChannelProfileColorEmojiApplied : R.string.UserProfileColorEmojiApplied)
                        ).show();
                    } else {
                        BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                            R.raw.contact_check,
                            getString(isChannel ? R.string.ChannelProfileColorResetApplied : R.string.UserProfileColorResetApplied)
                        ).show();
                    }
                } else {
                    BulletinFactory.of(bulletinFragment).createSimpleBulletin(
                        PeerColorDrawable.fromProfile(currentAccount, profilePage.selectedColor),
                        getString(isChannel ? R.string.ChannelProfileColorApplied : R.string.UserProfileColorApplied)
                    ).show();
                }
            }
            bulletinFragment = null;
        }
    }

    @Override
    public void onFragmentClosed() {
        super.onFragmentClosed();
        Bulletin.removeDelegate(this);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.starGiftsLoaded);
    }

    private List<TLRPC.TL_availableReaction> getAvailableReactions() {
        return getMediaDataController().getReactionsList();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_text_RedRegular,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        if (titleView != null) {
            titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        }
        namePage.updateColors();
        profilePage.updateColors();
        if (colorBar != null) {
            colorBar.updateColors();
        }
        setNavigationBarColor(getNavigationBarColor());
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            namePage.premiumChanged();
            profilePage.premiumChanged();
        } else if (id == NotificationCenter.starUserGiftsLoaded) {
            namePage.update();
            profilePage.update();
        } else if (id == NotificationCenter.starGiftsLoaded) {
            namePage.update();
            profilePage.update();
        }
    }

    public static class LevelLock extends Drawable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Text text;
        private final float lockScale = .875f;
        private final Drawable lock;
        private final PremiumGradient.PremiumGradientTools gradientTools;

        public LevelLock(Context context, int lvl, Theme.ResourcesProvider resourcesProvider) {
            this(context, false, lvl, resourcesProvider);
        }

        public LevelLock(Context context, boolean plus, int lvl, Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
            text = new Text(LocaleController.formatPluralString(plus ? "BoostLevelPlus" : "BoostLevel", lvl), 12, AndroidUtilities.bold());
            lock = context.getResources().getDrawable(R.drawable.mini_switch_lock).mutate();
            lock.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, -1, -1, -1, resourcesProvider);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int left = getBounds().left;
            int cy = getBounds().centerY();

            AndroidUtilities.rectTmp.set(left, cy - getIntrinsicHeight() / 2f, left + getIntrinsicWidth(), cy + getIntrinsicHeight() / 2f);
            gradientTools.gradientMatrix(AndroidUtilities.rectTmp);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), gradientTools.paint);

            lock.setBounds(
                left + dp(3.33f),
                (int) (cy - lock.getIntrinsicHeight() * lockScale / 2f),
                (int) (left + dp(3.33f) + lock.getIntrinsicWidth() * lockScale),
                (int) (cy + lock.getIntrinsicHeight() * lockScale / 2f)
            );
            lock.draw(canvas);

            text.draw(canvas, left + dp(3.66f) + lock.getIntrinsicWidth() * lockScale, cy, Color.WHITE, 1f);
        }

        @Override
        public void setAlpha(int alpha) {}
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (dp(3.66f + 6) + lock.getIntrinsicWidth() * lockScale + text.getWidth());
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(18.33f);
        }
    }

    public static CharSequence withLevelLock(CharSequence text, int lvl) {
        if (lvl <= 0) return text;
        final Context context = ApplicationLoader.applicationContext;
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        ssb.append("  L");
        LevelLock drawable = new LevelLock(context, lvl, null);
        ColoredImageSpan span = new ColoredImageSpan(drawable);
        span.setTranslateY(dp(1));
        ssb.setSpan(span, ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    public static class ChangeNameColorCell extends View {
        private final int currentAccount;
        private final boolean isChannelOrGroup;
        private final boolean isGroup;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Drawable drawable;
        private final Text buttonText;
        private LevelLock lock;

        private final Paint userTextBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Text userText;
        private int userTextColorKey = -1;
        private boolean needDivider;

        private PeerColorDrawable color1Drawable;
        private PeerColorDrawable color2Drawable;

        public ChangeNameColorCell(int currentAccount, long dialogId, Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            MessagesController mc = MessagesController.getInstance(currentAccount);
            TLRPC.Chat chat = mc.getChat(-dialogId);

            this.currentAccount = currentAccount;
            this.isChannelOrGroup = dialogId < 0;
            this.isGroup = isChannelOrGroup && !ChatObject.isChannelAndNotMegaGroup(chat);
            this.resourcesProvider = resourcesProvider;

            drawable = context.getResources().getDrawable(R.drawable.menu_edit_appearance).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            CharSequence button = getString(isChannelOrGroup ? (isGroup ? R.string.ChangeGroupAppearance : R.string.ChangeChannelNameColor2) : R.string.ChangeUserNameColor);
            if (isChannelOrGroup && !isGroup && MessagesController.getInstance(currentAccount).getMainSettings().getInt("boostingappearance", 0) < 3) {
                int minlvl = Integer.MAX_VALUE, maxlvl = 0;
                if (mc.peerColors != null) {
                    minlvl = Math.min(minlvl, mc.peerColors.maxLevel());
                    maxlvl = Math.max(maxlvl, mc.peerColors.maxLevel());
                    minlvl = Math.min(minlvl, mc.peerColors.minLevel());
                    maxlvl = Math.max(maxlvl, mc.peerColors.minLevel());
                }
                minlvl = Math.min(minlvl, mc.channelBgIconLevelMin);
                maxlvl = Math.min(maxlvl, mc.channelBgIconLevelMin);
                if (mc.profilePeerColors != null) {
                    minlvl = Math.min(minlvl, mc.profilePeerColors.maxLevel());
                    maxlvl = Math.max(maxlvl, mc.profilePeerColors.maxLevel());
                    minlvl = Math.min(minlvl, mc.profilePeerColors.minLevel());
                    maxlvl = Math.max(maxlvl, mc.profilePeerColors.minLevel());
                }
                minlvl = Math.min(minlvl, mc.channelProfileIconLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelProfileIconLevelMin);
                minlvl = Math.min(minlvl, mc.channelEmojiStatusLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelEmojiStatusLevelMin);
                minlvl = Math.min(minlvl, mc.channelWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelWallpaperLevelMin);
                minlvl = Math.min(minlvl, mc.channelCustomWallpaperLevelMin);
                maxlvl = Math.max(maxlvl, mc.channelCustomWallpaperLevelMin);
                int currentLevel = chat == null ? 0 : chat.level;
                if (currentLevel < maxlvl) {
                    lock = new LevelLock(context, true, Math.max(currentLevel, minlvl), resourcesProvider);
                }
            }
            if (isChannelOrGroup && lock == null) {
                button = TextCell.applyNewSpan(button);
            }
            buttonText = new Text(button, 16);
            updateColors();
        }

        public void updateColors() {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(isChannelOrGroup ? Theme.key_windowBackgroundWhiteGrayIcon : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider), PorterDuff.Mode.SRC_IN));
            buttonText.setColor(Theme.getColor(isChannelOrGroup ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));

            if (userText != null && userTextBackgroundPaint != null && userTextColorKey != -1) {
                final int color = Theme.getColor(userTextColorKey, resourcesProvider);
                userText.setColor(color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
            }
        }

        public void set(TLRPC.Chat chat, boolean divider) {
            if (chat == null) {
                return;
            }
            needDivider = divider;
            CharSequence text = chat.title;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.bold());

            if (color1Drawable != null) {
                color1Drawable.setView(null);
            }
            if (chat.emoji_status instanceof TLRPC.TL_emojiStatusCollectible) {
                color1Drawable = PeerColorDrawable.from((TLRPC.TL_emojiStatusCollectible) chat.emoji_status);
            } else {
                color1Drawable = ChatObject.getProfileColorId(chat) >= 0 ? PeerColorDrawable.fromProfile(currentAccount, ChatObject.getProfileColorId(chat)).setRadius(dp(11)) : null;
            }
            if (color1Drawable != null) {
                color1Drawable.setView(this);
            }
            final int color;
            if (chat.color instanceof TLRPC.TL_peerColorCollectible) {
                final TLRPC.TL_peerColorCollectible p = (TLRPC.TL_peerColorCollectible) chat.color;
                final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                final int accent_color = dark && (p.flags & 1) != 0 ? p.dark_accent_color : p.accent_color;
                final ArrayList<Integer> colors = dark && p.dark_colors != null ? p.dark_colors : p.colors;

                final int color1 = colors.get(0) | 0xFF000000;
                final int color2 = colors.size() >= 2 ? colors.get(1) | 0xFF000000 : color1;
                final int color3 = colors.size() >= 3 ? colors.get(2) | 0xFF000000 : color1;

                userText.setColor(accent_color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(accent_color, .10f));
                color2Drawable = new PeerColorDrawable(color1, color2, color3, p.gift_emoji_id).setRadius(dp(11));
                color2Drawable.setView(this);
            } else {
                int colorId = ChatObject.getColorId(chat);
                if (colorId < 7) {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
                } else {
                    MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                    MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                    if (peerColor != null) {
                        userTextColorKey = -1;
                        color = peerColor.getColor1();
                    } else {
                        color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                    }
                }

                userText.setColor(color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
                color2Drawable = PeerColorDrawable.from(currentAccount, colorId).setRadius(dp(11));
                if (color2Drawable != null) {
                    color2Drawable.setView(this);
                }
            }
        }

        public void set(TLRPC.User user) {
            if (user == null) {
                return;
            }
            String name = user.first_name == null ? "" : user.first_name.trim();
            int index = name.indexOf(" ");
            if (index > 0) {
                name = name.substring(0, index);
            }
            CharSequence text = name;
            text = Emoji.replaceEmoji(text, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
            userText = new Text(text, 13, AndroidUtilities.bold());
            if (color1Drawable != null) {
                color1Drawable.setView(null);
            }
            if (user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible) {
                color1Drawable = PeerColorDrawable.from((TLRPC.TL_emojiStatusCollectible) user.emoji_status);
            } else {
                color1Drawable = UserObject.getProfileColorId(user) >= 0 ? PeerColorDrawable.fromProfile(currentAccount, UserObject.getProfileColorId(user)).setRadius(dp(11)) : null;
            }
            if (color1Drawable != null) {
                color1Drawable.setView(this);
            }
            if (user.color instanceof TLRPC.TL_peerColorCollectible) {
                final TLRPC.TL_peerColorCollectible p = (TLRPC.TL_peerColorCollectible) user.color;
                final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                final int accent_color = dark && (p.flags & 1) != 0 ? p.dark_accent_color : p.accent_color;
                final ArrayList<Integer> colors = dark && p.dark_colors != null ? p.dark_colors : p.colors;

                final int color1 = colors.get(0) | 0xFF000000;
                final int color2 = colors.size() >= 2 ? colors.get(1) | 0xFF000000 : color1;
                final int color3 = colors.size() >= 3 ? colors.get(2) | 0xFF000000 : color1;

                userText.setColor(accent_color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(accent_color, .10f));
                color2Drawable = new PeerColorDrawable(color1, color2, color3, p.gift_emoji_id).setRadius(dp(11));
                color2Drawable.setView(this);
            } else {
                final int color;
                int colorId = UserObject.getColorId(user);
                if (colorId < 7) {
                    color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[colorId], resourcesProvider);
                } else {
                    MessagesController.PeerColors peerColors = MessagesController.getInstance(UserConfig.selectedAccount).peerColors;
                    MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
                    if (peerColor != null) {
                        userTextColorKey = -1;
                        color = peerColor.getColor1();
                    } else {
                        color = Theme.getColor(userTextColorKey = Theme.keys_avatar_nameInMessage[0], resourcesProvider);
                    }
                }
                userText.setColor(color);
                userTextBackgroundPaint.setColor(Theme.multAlpha(color, .10f));
                color2Drawable = PeerColorDrawable.from(currentAccount, colorId).setRadius(dp(11));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        private int rtl(int x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }
        private float rtl(float x) {
            return LocaleController.isRTL ? getMeasuredWidth() - x : x;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            drawable.setBounds(
                rtl(dp(64) / 2) - drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 - drawable.getIntrinsicHeight() / 2,
                rtl(dp(64) / 2) + drawable.getIntrinsicWidth() / 2,
                getMeasuredHeight() / 2 + drawable.getIntrinsicHeight() / 2
            );
            drawable.draw(canvas);
            buttonText.ellipsize(getMeasuredWidth() - dp(64 + 7 + 100) - (lock != null ? lock.getIntrinsicWidth() + dp(8) : 0));
            float textX = LocaleController.isRTL ? getMeasuredWidth() - buttonText.getWidth() - dp(64 + 7) : dp(64 + 7);
            buttonText.draw(canvas, textX, getMeasuredHeight() / 2f);
            if (lock != null) {
                int x = (int) (textX + buttonText.getWidth() + dp(6));
                lock.setBounds(x, 0, x, getHeight());
                lock.draw(canvas);
            }

            if (isGroup && color2Drawable != null) {
                int x = LocaleController.isRTL ? dp(24 + 16 + 18) : getMeasuredWidth() - dp(24);
                color2Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color2Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color2Drawable.draw(canvas);
            } else if (color1Drawable != null && color2Drawable != null) {

                int x = LocaleController.isRTL ? dp(24 + 16 + 18) : getMeasuredWidth() - dp(24);
                color2Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color2Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color2Drawable.draw(canvas);

                x -= dp(18);
                color1Drawable.setBounds(x - dp(11), (getMeasuredHeight() - dp(11)) / 2, x, (getMeasuredHeight() + dp(11)) / 2);
                color1Drawable.stroke(dpf2(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                color1Drawable.draw(canvas);

            } else if (userText != null && !isGroup) {
                final int maxWidth = (int) (getMeasuredWidth() - dp(64 + 7 + 15 + 9 + 9 + 12) - Math.min(buttonText.getWidth() + (lock == null ? 0 : lock.getIntrinsicWidth() + dp(6 + 6)), getMeasuredWidth() - dp(64 + 100)));
                final int w = (int) Math.min(userText.getWidth(), maxWidth);

                AndroidUtilities.rectTmp.set(
                        LocaleController.isRTL ? dp(15) : getMeasuredWidth() - dp(15 + 9 + 9) - w,
                        (getMeasuredHeight() - dp(22)) / 2f,
                        LocaleController.isRTL ? dp(15 + 9 + 9) + w : getMeasuredWidth() - dp(15),
                        (getMeasuredHeight() + dp(22)) / 2f
                );
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), userTextBackgroundPaint);

                userText
                    .ellipsize(maxWidth)
                    .draw(canvas, LocaleController.isRTL ? dp(15 + 9) : getMeasuredWidth() - dp(15 + 9) - w, getMeasuredHeight() / 2f);
            }

            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, paint);
            }
        }
    }

    public static class PeerColorGrid extends View {
        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        { backgroundPaint.setStyle(Paint.Style.STROKE); }

        public static final int TYPE_FOLDER_TAG = 2;

        public class ColorButton {
            private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path circlePath = new Path();
            private final Path color2Path = new Path();
            private boolean hasColor2, hasColor3;
            private boolean hasClose;

            private Path closePath;
            private Paint closePaint;
            private Drawable lockDrawable;

            private final ButtonBounce bounce = new ButtonBounce(PeerColorGrid.this);

            public ColorButton() {}

            public void set(int color) {
                hasColor2 = hasColor3 = false;
                paint1.setColor(color);
            }

            public void set(int color1, int color2) {
                hasColor2 = true;
                hasColor3 = false;
                paint1.setColor(color1);
                paint2.setColor(color2);
            }

            public void setClose(boolean close) {
                hasClose = close;
            }

            public void set(MessagesController.PeerColor color) {
                if (color == null) {
                    return;
                }
                final boolean dark = resourcesProvider == null ? Theme.isCurrentThemeDark() : resourcesProvider.isDark();
                if (type == PAGE_NAME) {
                    if (dark && color.hasColor2() && !color.hasColor3()) {
                        paint1.setColor(color.getColor(1, resourcesProvider));
                        paint2.setColor(color.getColor(0, resourcesProvider));
                    } else {
                        paint1.setColor(color.getColor(0, resourcesProvider));
                        paint2.setColor(color.getColor(1, resourcesProvider));
                    }
                    paint3.setColor(color.getColor(2, resourcesProvider));
                    hasColor2 = color.hasColor2(dark);
                    hasColor3 = color.hasColor3(dark);
                } else {
                    paint1.setColor(color.getColor(0, resourcesProvider));
                    paint2.setColor(color.hasColor6(dark) ? color.getColor(1, resourcesProvider) : color.getColor(0, resourcesProvider));
                    hasColor2 = color.hasColor6(dark);
                    hasColor3 = false;
                }
            }

            private boolean selected;
            private final AnimatedFloat selectedT = new AnimatedFloat(PeerColorGrid.this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            public void setSelected(boolean selected, boolean animated) {
                this.selected = selected;
                if (!animated) {
                    selectedT.set(selected, true);
                }
                invalidate();
            }

            public int id;
            private final RectF bounds = new RectF();
            public final RectF clickBounds = new RectF();
            public void layout(RectF bounds) {
                this.bounds.set(bounds);
            }
            public void layoutClickBounds(RectF bounds) {
                this.clickBounds.set(bounds);
            }

            protected void draw(Canvas canvas) {
                canvas.save();
                final float s = bounce.getScale(.05f);
                canvas.scale(s, s, bounds.centerX(), bounds.centerY());

                canvas.save();
                circlePath.rewind();
                circlePath.addCircle(bounds.centerX(), bounds.centerY(), Math.min(bounds.height() / 2f, bounds.width() / 2f), Path.Direction.CW);
                canvas.clipPath(circlePath);
                canvas.drawPaint(paint1);
                if (hasColor2) {
                    color2Path.rewind();
                    color2Path.moveTo(bounds.right, bounds.top);
                    color2Path.lineTo(bounds.right, bounds.bottom);
                    color2Path.lineTo(bounds.left, bounds.bottom);
                    color2Path.close();
                    canvas.drawPath(color2Path, paint2);
                }
                canvas.restore();

                if (hasColor3) {
                    canvas.save();
                    final float color3Size = (bounds.width() * .315f);
                    AndroidUtilities.rectTmp.set(
                        bounds.centerX() - color3Size / 2f,
                        bounds.centerY() - color3Size / 2f,
                        bounds.centerX() + color3Size / 2f,
                        bounds.centerY() + color3Size / 2f
                    );
                    canvas.rotate(45f, bounds.centerX(), bounds.centerY());
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), paint3);
                    canvas.restore();
                }

                final float selectT = selectedT.set(selected);

                if (selectT > 0) {
                    backgroundPaint.setStrokeWidth(dpf2(2));
                    backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    canvas.drawCircle(
                        bounds.centerX(), bounds.centerY(),
                        Math.min(bounds.height() / 2f, bounds.width() / 2f) + backgroundPaint.getStrokeWidth() * lerp(.5f, -2f, selectT),
                        backgroundPaint
                    );
                }

                if (hasClose) {
                    if (lock) {
                        if (lockDrawable == null) {
                            lockDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_lock3);
                            lockDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                        }
                        lockDrawable.setBounds(
                            (int) (bounds.centerX() - lockDrawable.getIntrinsicWidth() / 2f * 1.2f),
                            (int) (bounds.centerY() - lockDrawable.getIntrinsicHeight() / 2f * 1.2f),
                            (int) (bounds.centerX() + lockDrawable.getIntrinsicWidth() / 2f * 1.2f),
                            (int) (bounds.centerY() + lockDrawable.getIntrinsicHeight() / 2f * 1.2f)
                        );
                        lockDrawable.draw(canvas);
                    } else {
                        if (closePath == null) {
                            closePath = new Path();
                        }
                        if (closePaint == null) {
                            closePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            closePaint.setColor(0xffffffff);
                            closePaint.setStyle(Paint.Style.STROKE);
                            closePaint.setStrokeCap(Paint.Cap.ROUND);
                        }
                        closePaint.setStrokeWidth(dp(2));
                        closePath.rewind();
                        final float r = lerp(dp(5), dp(4), selectT);
                        closePath.moveTo(bounds.centerX() - r, bounds.centerY() - r);
                        closePath.lineTo(bounds.centerX() + r, bounds.centerY() + r);
                        closePath.moveTo(bounds.centerX() + r, bounds.centerY() - r);
                        closePath.lineTo(bounds.centerX() - r, bounds.centerY() + r);
                        canvas.drawPath(closePath, closePaint);
                    }
                }

                canvas.restore();
            }

            private boolean pressed;
            public boolean isPressed() {
                return pressed;
            }

            public void setPressed(boolean pressed) {
                bounce.setPressed(this.pressed = pressed);
            }
        }

        private final int type;
        private final int currentAccount;
        private boolean lock;

        private ColorButton[] buttons;

        public PeerColorGrid(Context context, int type, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.type = type;
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
        }

        public void setCloseAsLock(boolean lock) {
            this.lock = lock;
        }

        public void updateColors() {
            if (buttons == null) return;
            final MessagesController mc = MessagesController.getInstance(currentAccount);
            final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
            for (int i = 0; i < buttons.length; ++i) {
                if (type == TYPE_FOLDER_TAG) {
                    buttons[i].id = order[i];
                    buttons[i].setClose(buttons[i].id < 0);
                    buttons[i].set(Theme.getColor(order[i] < 0 ? Theme.key_avatar_backgroundGray : Theme.keys_avatar_nameInMessage[order[i] % Theme.keys_avatar_nameInMessage.length], resourcesProvider));
                } else if (i < 7 && type == PAGE_NAME) {
                    buttons[i].id = order[i];
                    buttons[i].set(Theme.getColor(Theme.keys_avatar_nameInMessage[order[i]], resourcesProvider));
                } else {
                    final int id = i;
                    if (peerColors != null && id >= 0 && id < peerColors.colors.size()) {
                        buttons[i].id = peerColors.colors.get(id).id;
                        buttons[i].set(peerColors.colors.get(id));
                    }
                }
            }
            invalidate();
        }
        final int[] order = new int[] { 5, 3, 1, 0, 2, 4, 6, -1 };

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);

            final MessagesController mc = MessagesController.getInstance(currentAccount);
            final MessagesController.PeerColors peerColors = type == PAGE_NAME ? mc.peerColors : mc.profilePeerColors;
            int colorsCount = peerColors == null ? 0 : peerColors.colors.size();
            if (type == TYPE_FOLDER_TAG) {
                colorsCount = 8;
            }
            final int columns;
            if (type == TYPE_FOLDER_TAG) {
                columns = 8;
            } else if (type == PAGE_NAME) {
                columns = 7;
            } else {
                columns = 8;
            }

            final float iconSize = Math.min(dp(38 + 16), width / (columns + (columns + 1) * .28947f));
            final float horizontalSeparator = Math.min(iconSize * .28947f, dp(8));
            final float verticalSeparator = Math.min(iconSize * .315789474f, dp(11.33f));

            final int rows = colorsCount / columns;
            final int height = (int) (iconSize * rows + verticalSeparator * (rows + 1));

            setMeasuredDimension(width, height);

            if (buttons == null || buttons.length != colorsCount) {
                buttons = new ColorButton[colorsCount];
                for (int i = 0; i < colorsCount; ++i) {
                    buttons[i] = new ColorButton();
                    if (type == TYPE_FOLDER_TAG) {
                        buttons[i].id = order[i];
                        buttons[i].setClose(buttons[i].id < 0);
                        buttons[i].set(Theme.getColor(order[i] < 0 ? Theme.key_avatar_backgroundGray : Theme.keys_avatar_nameInMessage[order[i] % Theme.keys_avatar_nameInMessage.length], resourcesProvider));
                    } else if (peerColors != null && i >= 0 && i < peerColors.colors.size()) {
                        buttons[i].id = peerColors.colors.get(i).id;
                        buttons[i].set(peerColors.colors.get(i));
                    }
                }
            }
            final float itemsWidth = iconSize * columns + horizontalSeparator * (columns + 1);
            final float startX = (width - itemsWidth) / 2f + horizontalSeparator;
            if (buttons != null) {
                float x = startX, y = verticalSeparator;
                for (int i = 0; i < buttons.length; ++i) {
                    AndroidUtilities.rectTmp.set(x, y, x + iconSize, y + iconSize);
                    buttons[i].layout(AndroidUtilities.rectTmp);
                    AndroidUtilities.rectTmp.inset(-horizontalSeparator / 2, -verticalSeparator / 2);
                    buttons[i].layoutClickBounds(AndroidUtilities.rectTmp);
                    buttons[i].setSelected(buttons[i].id == selectedColorId, false);

                    if (i % columns == (columns - 1)) {
                        x = startX;
                        y += iconSize + verticalSeparator;
                    } else {
                        x += iconSize + horizontalSeparator;
                    }
                }
            }
        }

        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider = true;
        public void setDivider(boolean needDivider) {
            this.needDivider = needDivider;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].draw(canvas);
                }
            }
            if (needDivider) {
                dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                canvas.drawRect(dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - dp(21), getMeasuredHeight(), dividerPaint);
            }
        }

        private int selectedColorId = 0;
        public void setSelected(int colorId, boolean animated) {
            selectedColorId = colorId;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    buttons[i].setSelected(buttons[i].id == colorId, animated);
                }
            }
        }
        public int getColorId() {
            return selectedColorId;
        }

        private Utilities.Callback<Integer> onColorClick;
        public void setOnColorClick(Utilities.Callback<Integer> onColorClick) {
            this.onColorClick = onColorClick;
        }

        private ColorButton pressedButton;
        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            ColorButton button = null;
            if (buttons != null) {
                for (int i = 0; i < buttons.length; ++i) {
                    if (buttons[i].clickBounds.contains(event.getX(), event.getY())) {
                        button = buttons[i];
                        break;
                    }
                }
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressedButton = button;
                if (button != null) {
                    button.setPressed(true);
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressedButton != button) {
                    if (pressedButton != null) {
                        pressedButton.setPressed(false);
                    }
                    if (button != null) {
                        button.setPressed(true);
                    }
                    if (pressedButton != null && button != null) {
                        if (onColorClick != null) {
                            onColorClick.run(button.id);
                        }
                    }
                    pressedButton = button;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (event.getAction() == MotionEvent.ACTION_UP && pressedButton != null) {
                    if (onColorClick != null) {
                        onColorClick.run(pressedButton.id);
                    }
                }
                if (buttons != null) {
                    for (int i = 0; i < buttons.length; ++i) {
                        buttons[i].setPressed(false);
                    }
                }
                pressedButton = null;
            }
            return true;
        }
    }

    public static class PeerColorSpan extends ReplacementSpan {
        private int size = dp(21);
        public PeerColorDrawable drawable;

        public PeerColorSpan(boolean profile, int currentAccount, int colorId) {
            drawable = profile ? PeerColorDrawable.fromProfile(currentAccount, colorId) : PeerColorDrawable.from(currentAccount, colorId);
        }

        public PeerColorSpan setSize(int sz) {
            if (drawable != null) {
                drawable.setRadius(sz / 2f);
                size = sz;
            }
            return this;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return dp(3) + size + dp(3);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            if (drawable != null) {
                int cy = (top + bottom) / 2;
                drawable.setBounds((int) (x + dp(3)), cy - size, (int) (x + dp(5) + size), cy + size);
                drawable.draw(canvas);
            }
        }
    }

    public static class PeerColorDrawable extends Drawable {

        public static PeerColorDrawable from(TLRPC.TL_peerColorCollectible peerColorCollectible) {
            final boolean dark = Theme.isCurrentThemeDark();
            final ArrayList<Integer> colors = dark && peerColorCollectible.dark_colors != null ? peerColorCollectible.dark_colors : peerColorCollectible.colors;
            if (colors == null || colors.isEmpty())
                return null;

            final int color1 = colors.get(0) | 0xFF000000;
            final int color2 = colors.size() >= 2 ? colors.get(1) | 0xFF000000 : color1;
            final int color3 = colors.size() >= 3 ? colors.get(2) | 0xFF000000 : color1;

            return new PeerColorDrawable(color1, color2, color3, peerColorCollectible.gift_emoji_id);
        }

        public static PeerColorDrawable from(TLRPC.TL_emojiStatusCollectible emojiStatus) {
            final int color = emojiStatus.center_color | 0xFF000000;
            return new PeerColorDrawable(color, color, color, emojiStatus.document_id);
        }

        public static PeerColorDrawable from(int currentAccount, int colorId) {
            if (colorId < 7) {
                return new PeerColorDrawable(Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]), Theme.getColor(Theme.keys_avatar_nameInMessage[colorId]));
            }
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).peerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            return from(peerColor, false);
        }

        public static PeerColorDrawable fromProfile(int currentAccount, int colorId) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            return from(peerColor, true);
        }

        public static PeerColorDrawable from(MessagesController.PeerColor peerColor, boolean fromProfile) {
            if (peerColor == null) {
                return new PeerColorDrawable(0, 0, 0);
            }
            return new PeerColorDrawable(peerColor.getColor1(), !fromProfile || peerColor.hasColor6(Theme.isCurrentThemeDark()) ? peerColor.getColor2() : peerColor.getColor1(), fromProfile ? peerColor.getColor1() : peerColor.getColor3());
        }

        private float radius = dpf2(21.333f / 2f);

        public PeerColorDrawable setRadius(float r) {
            this.radius = r;
            initPath();
            return this;
        }

        public PeerColorDrawable stroke(float width, int color) {
            if (strokePaint == null) {
                strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setStyle(Paint.Style.STROKE);
            }
            strokePaint.setStrokeWidth(width);
            strokePaint.setColor(color);
            return this;
        }

        private final boolean hasColor3;
        private Paint strokePaint;
        private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint color3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path color2Path = new Path();
        private final Path clipCirclePath = new Path();

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;

        public PeerColorDrawable(int color1, int color2, int color3) {
            hasColor3 = color3 != color1;
            color1Paint.setColor(color1);
            color2Paint.setColor(color2);
            color3Paint.setColor(color3);
            emoji = null;

            initPath();
        }

        public PeerColorDrawable(int color1, int color2, int color3, long emojiId) {
            hasColor3 = color3 != color1;
            color1Paint.setColor(color1);
            color2Paint.setColor(color2);
            color3Paint.setColor(color3);

            initPath();

            emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, dp(14));
            emoji.set(emojiId, false);
        }

        public PeerColorDrawable setView(View view) {
            if (view == null) {
                if (emoji != null) {
                    emoji.detach();
                    emoji.setParentView(null);
                }
                return this;
            }
            if (emoji != null) {
                emoji.setParentView(view);
            }
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View view) {
                    if (emoji != null) {
                        emoji.attach();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View view) {
                    if (emoji != null) {
                        emoji.detach();
                    }
                }
            });
            return this;
        }

        private void initPath() {
            clipCirclePath.rewind();
            clipCirclePath.addCircle(radius, radius, radius, Path.Direction.CW);
            color2Path.rewind();
            color2Path.moveTo(radius * 2, 0);
            color2Path.lineTo(radius * 2, radius * 2);
            color2Path.lineTo(0, radius * 2);
            color2Path.close();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().centerX() - radius, getBounds().centerY() - radius);
            if (strokePaint != null) {
                canvas.drawCircle(radius, radius, radius, strokePaint);
            }
            canvas.clipPath(clipCirclePath);
            canvas.drawPaint(color1Paint);
            canvas.drawPath(color2Path, color2Paint);
            if (hasColor3) {
                AndroidUtilities.rectTmp.set(radius - dp(3.66f), radius - dp(3.66f), radius + dp(3.66f), radius + dp(3.66f));
                canvas.rotate(45, radius, radius);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(2.33f), dp(2.33f), color3Paint);
            }
            canvas.restore();
            if (emoji != null) {
                final int sz = dp(14);
                emoji.setBounds(
                    getBounds().centerX() - sz / 2,
                    getBounds().centerY() - sz / 2,
                    getBounds().centerX() + sz / 2,
                    getBounds().centerY() + sz / 2
                );
                emoji.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) (radius * 2);
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (radius * 2);
        }
    }

    public static class ColoredActionBar extends View {

        private int defaultColor;
        private final Theme.ResourcesProvider resourcesProvider;

        public ColoredActionBar(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            defaultColor = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            setColor(-1, -1, false);
        }

        public void setColor(int currentAccount, int colorId, boolean animated) {
            MessagesController.PeerColor peerColor = null;
            if (colorId >= 0 && currentAccount >= 0) {
                MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
                peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            }
            setColor(peerColor, animated);
        }

        public void setColor(MessagesController.PeerColor peerColor, boolean animated) {
            isDefault = false;
            if (peerColor == null) {
                isDefault = true;
                color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            } else {
                if (peerColor != null) {
                    final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                    color1 = peerColor.getBgColor1(isDark);
                    color2 = peerColor.getBgColor2(isDark);
                } else {
                    isDefault = true;
                    color1 = color2 = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
                }
            }
            if (!animated) {
                color1Animated.set(color1, true);
                color2Animated.set(color2, true);
            }
            invalidate();
        }

        private float progressToGradient = 0;
        public void setProgressToGradient(float progress) {
            if (Math.abs(progressToGradient - progress) > 0.001f) {
                progressToGradient = progress;
                onUpdateColor();
                invalidate();
            }
        }

        public boolean isDefault;
        public int color1, color2;
        private final AnimatedColor color1Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedColor color2Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        private int backgroundGradientColor1, backgroundGradientColor2, backgroundGradientWidth, backgroundGradientHeight;
        private RadialGradient backgroundGradient;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        protected void onUpdateColor() {

        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final int color1 = color1Animated.set(this.color1);
            final int color2 = color2Animated.set(this.color2);
            if (backgroundGradient == null || backgroundGradientColor1 != color1 || backgroundGradientColor2 != color2 || backgroundGradientWidth != getWidth() || backgroundGradientHeight != getHeight()) {
                backgroundGradientWidth = getWidth();
                backgroundGradientHeight = getHeight();
                backgroundGradient = new RadialGradient(
                    backgroundGradientWidth / 2f, backgroundGradientHeight * 0.40f,
                    distance(0, 0, backgroundGradientWidth, backgroundGradientHeight) * 0.75f,
                    new int[] { backgroundGradientColor2 = color2, backgroundGradientColor1 = color1 },
                    new float[] { 0, 1 },
                    Shader.TileMode.CLAMP
                );
                backgroundPaint.setShader(backgroundGradient);
                onUpdateColor();
            }
            if (progressToGradient < 1) {
                canvas.drawColor(defaultColor);
            }
            if (progressToGradient > 0) {
                backgroundPaint.setAlpha((int) (0xFF * progressToGradient));
                canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            }
        }

        protected boolean ignoreMeasure;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, ignoreMeasure ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(AndroidUtilities.statusBarHeight + dp(230), MeasureSpec.EXACTLY));
        }

        public void updateColors() {
            defaultColor = Theme.getColor(Theme.key_actionBarDefault, resourcesProvider);
            onUpdateColor();
            invalidate();
        }

        public int getColor() {
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider), ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f), progressToGradient);
        }

        public int getActionBarButtonColor() {
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), isDefault ? Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider) : Color.WHITE, progressToGradient);
        }

        public int getTabsViewBackgroundColor() {
            return (
                ColorUtils.blendARGB(
                    AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider)) > .721f ?
                        Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider) :
                        Theme.adaptHSV(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider), +.08f, -.08f),
                    AndroidUtilities.computePerceivedBrightness(ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f)) > .721f ?
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider) :
                        Theme.adaptHSV(ColorUtils.blendARGB(color1Animated.get(), color2Animated.get(), .75f), +.08f, -.08f),
                    progressToGradient
                )
            );
        }
    }

    public static class ProfilePreview extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final int currentAccount;
        private final long dialogId;
        private final boolean isChannel;

        protected final ImageReceiver imageReceiver = new ImageReceiver(this);
        protected final AvatarDrawable avatarDrawable = new AvatarDrawable();
        protected final SimpleTextView titleView, subtitleView;

        private boolean isForum;

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerificationEmoji;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusEmoji;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
        private final StoriesUtilities.StoryGradientTools storyGradient = new StoriesUtilities.StoryGradientTools(this, false);

        private boolean isEmojiCollectible;
        private final AnimatedFloat emojiCollectible = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public ProfilePreview(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.resourcesProvider = resourcesProvider;
            this.isChannel = dialogId < 0;

            titleView = new SimpleTextView(context) {
                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    statusEmoji.attach();
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    statusEmoji.detach();
                }
            };
            botVerificationEmoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleView, dp(17));
            statusEmoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleView, dp(24));
            titleView.setLeftDrawableOutside(true);
            titleView.setRightDrawableOutside(true);
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setTextSize(20);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setWidthWrapContent(true);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 40.33f));

            subtitleView = new SimpleTextView(context);
            subtitleView.setTextSize(14);
            subtitleView.setTextColor(0x80FFFFFF);
            subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 20.66f));

            imageReceiver.setRoundRadius(dp(96));
            long botVerificationId = 0, emojiStatusId = 0;
            CharSequence title;
            if (isChannel) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                title = chat == null ? "" : chat.title;

                avatarDrawable.setInfo(currentAccount, chat);
                imageReceiver.setForUserOrChat(chat, avatarDrawable);

                botVerificationId = DialogObject.getBotVerificationIcon(chat);
                emojiStatusId = chat != null ? DialogObject.getEmojiStatusDocumentId(chat.emoji_status) : 0;
            } else {
                TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                title = UserObject.getUserName(user);

                avatarDrawable.setInfo(currentAccount, user);
                imageReceiver.setForUserOrChat(user, avatarDrawable);

                botVerificationId = DialogObject.getBotVerificationIcon(user);
                emojiStatusId = user != null ? DialogObject.getEmojiStatusDocumentId(user.emoji_status) : 0;
            }
            try {
                title = Emoji.replaceEmoji(title, null, false);
            } catch (Exception ignore) {
            }

            titleView.setText(title);
            botVerificationEmoji.set(botVerificationId, false);
            titleView.setLeftDrawable(botVerificationEmoji);
            statusEmoji.set(emojiStatusId, false);
            titleView.setRightDrawable(statusEmoji);

            if (isChannel) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                if (chatFull != null && chatFull.participants_count > 0) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Subscribers", chatFull.participants_count));
                    } else {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Members", chatFull.participants_count));
                    }
                } else if (chat != null && chat.participants_count > 0) {
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Subscribers", chat.participants_count));
                    } else {
                        subtitleView.setText(LocaleController.formatPluralStringComma("Members", chat.participants_count));
                    }
                } else {
                    final boolean isPublic = ChatObject.isPublic(chat);
                    if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                        subtitleView.setText(getString(isPublic ? R.string.ChannelPublic : R.string.ChannelPrivate).toLowerCase());
                    } else {
                        subtitleView.setText(getString(isPublic ? R.string.MegaPublic : R.string.MegaPrivate).toLowerCase());
                    }
                }
            } else {
                subtitleView.setText(getString(R.string.Online));
            }

            setWillNotDraw(false);
        }

        public void overrideAvatarColor(int colorId) {
            final int color1, color2;
            if (colorId >= 14) {
                MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
                MessagesController.PeerColors peerColors = messagesController != null ? messagesController.peerColors : null;
                MessagesController.PeerColor peerColor = peerColors != null ? peerColors.getColor(colorId) : null;
                if (peerColor != null) {
                    final int peerColorValue = peerColor.getColor1();
                    color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getPeerColorIndex(peerColorValue)]);
                } else {
                    color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                    color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
                }
            } else {
                color1 = getThemedColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(colorId)]);
                color2 = getThemedColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(colorId)]);
            }
            avatarDrawable.setColor(color1, color2);
            invalidate();
        }

        public void setForum(boolean forum) {
            if (isForum != forum) {
                invalidate();
            }
            isForum = forum;
        }
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            emoji.attach();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            emoji.detach();
            imageReceiver.onDetachedFromWindow();
        }

        private int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        private MessagesController.PeerColor peerColor;
        public void setColor(int colorId, boolean animated) {
            MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(colorId);
            setColor(peerColor, animated);
        }

        public void setColor(MessagesController.PeerColor peerColor, boolean animated) {
            this.peerColor = peerColor;
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                if (peerColor.patternColor != 0) {
                    emoji.setColor(peerColor.patternColor);
                } else {
                    emoji.setColor(adaptProfileEmojiColor(peerColor.getBgColor1(isDark)));
                }
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getStoryColor1(Theme.isCurrentThemeDark()), 0xFFFFFFFF, 0.25f));
                botVerificationEmoji.setColor(ColorUtils.blendARGB(peerColor.getStoryColor1(Theme.isCurrentThemeDark()), 0xFFFFFFFF, 0.25f));
                final int accentColor = ColorUtils.blendARGB(peerColor.getStoryColor1(isDark), peerColor.getStoryColor2(isDark), .5f);
                if (!Theme.hasHue(getThemedColor(Theme.key_actionBarDefault))) {
                    subtitleView.setTextColor(accentColor);
                } else {
                    subtitleView.setTextColor(Theme.changeColorAccent(getThemedColor(Theme.key_actionBarDefault), accentColor, getThemedColor(Theme.key_avatar_subtitleInProfileBlue), isDark, accentColor));
                }
                titleView.setTextColor(Color.WHITE);
            } else {
                if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                    emoji.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
                } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                    emoji.setColor(Theme.multAlpha(getThemedColor(Theme.key_actionBarDefaultTitle), .5f));
                } else {
                    emoji.setColor(adaptProfileEmojiColor(getThemedColor(Theme.key_actionBarDefault)));
                }
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
                botVerificationEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
                subtitleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            }

            storyGradient.setColor(peerColor, animated);
            invalidate();
        }

        public void setEmoji(long docId, boolean isCollectible, boolean animated) {
            if (docId == 0) {
                emoji.set((Drawable) null, animated);
            } else {
                emoji.set(docId, animated);
            }
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                if (peerColor.patternColor != 0) {
                    emoji.setColor(peerColor.patternColor);
                } else {
                    emoji.setColor(adaptProfileEmojiColor(peerColor.getBgColor1(isDark)));
                }
            } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                emoji.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                emoji.setColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle), .5f));
            } else {
                emoji.setColor(adaptProfileEmojiColor(Theme.getColor(Theme.key_actionBarDefault)));
            }
            if (peerColor != null) {
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getColor(1, resourcesProvider), peerColor.hasColor6(isDark) ? peerColor.getColor(4, resourcesProvider) : peerColor.getColor(2, resourcesProvider), .5f));
            } else {
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            }
            isEmojiCollectible = isCollectible;
            if (!animated) {
                emojiCollectible.force(isEmojiCollectible);
            }
            invalidate();
        }

        public void setStatusEmoji(long docId, boolean isCollectible, boolean animated) {
            statusEmoji.set(docId, animated);
            statusEmoji.setParticles(isCollectible, animated);
            final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            if (peerColor != null) {
                statusEmoji.setColor(ColorUtils.blendARGB(peerColor.getColor2(isDark), peerColor.hasColor6(isDark) ? peerColor.getColor5(isDark) : peerColor.getColor3(isDark), .5f));
            } else {
                statusEmoji.setColor(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider));
            }
        }

        private final RectF rectF = new RectF();
        @Override
        protected void dispatchDraw(Canvas canvas) {
            rectF.set(
                (getWidth() - dp(86)) / 2f,
                getHeight() - dp(82 + 86),
                (getWidth() + dp(86)) / 2f,
                getHeight() - dp(82)
            );
            imageReceiver.setRoundRadius(isForum ? dp(18) : dp(54));
            imageReceiver.setImageCoords(rectF);
            imageReceiver.draw(canvas);

            final float r = rectF.width() / 2f + dp(4);
            final float rr = dp(isForum ? 22 : 58);
            canvas.drawRoundRect(
                rectF.centerX() - r,
                rectF.centerY() - r,
                rectF.centerX() + r,
                rectF.centerY() + r,
                rr, rr,
                storyGradient.getPaint(rectF)
            );

//            final float patternFull = emojiCollectible.set(isEmojiCollectible);
//            StarGiftPatterns.drawProfilePattern(canvas, emoji, getWidth(), getHeight(), 1.0f, patternFull);
            StarGiftPatterns.drawProfileAnimatedPattern(
                canvas,
                emoji,
                getWidth(),
                getHeight(),
                1.0f,
                rectF,
                1.0f
            );

            super.dispatchDraw(canvas);
        }
    }

    public static int adaptProfileEmojiColor(int color) {
        final boolean isDark = AndroidUtilities.computePerceivedBrightness(color) < .2f;
        return Theme.adaptHSV(color, +.5f, isDark ? +.28f : -.28f);
    }

    public static final float PARTICLE_SIZE_DP = 24;
    public static final int PARTICLES_COUNT = 15;
    public static final float GOLDEN_RATIO_ANGLE = 139f;
    public static final float FILL_SCALE = 1;

    public static void drawSunflowerPattern(float cx, float cy, Utilities.Callback3<Float, Float, Float> draw) {
        drawSunflowerPattern(PARTICLES_COUNT, cx, cy, 30, dp(PARTICLE_SIZE_DP) * .7f, 1.4f, GOLDEN_RATIO_ANGLE, draw);
    }

    public static void drawSunflowerPattern(int count, float cx, float cy, float anglestart, float scale, float scale2, float angle, Utilities.Callback3<Float, Float, Float> draw) {
        for (int i = 1; i <= count; ++i) {
            final float a = anglestart + i * angle;
            final float r = (float) (Math.sqrt(i * scale2) * scale);
            final float x = (float) (cx + Math.cos(a / 180f * Math.PI) * r) + (i == 3 ? .3f * scale : 0);
            final float y = (float) (cy + Math.sin(a / 180f * Math.PI) * r) + (i == 3 ? -.5f * scale : 0);
            draw.run(x, y, (float) Math.sqrt(1f - (float) i / count));
        }
    }

    private final static float[] particles = {
        -18, -24.66f, 24, .4f,
        5.33f, -53, 28, .38f,
        -4, -86, 19, .18f,
        31, -30, 21, .35f,
        12, -3, 24, .18f,
        30, -73, 19, .3f,
        43, -101, 16, .1f,
        -50, 1.33f, 20, .22f,
        -58, -33, 24, .22f,
        -35, -62, 25, .22f,
        -59, -88, 19, .18f,
        -86, -61, 19, .1f,
        -90, -14.33f, 19.66f, .18f
    };
    public static void drawProfileIconPattern(float cx, float cy, float scale, Utilities.Callback4<Float, Float, Float, Float> draw) {
        for (int i = 0; i < particles.length; i += 4) {
            draw.run(
                cx + dp(particles[i]) * scale,
                cy + dp(particles[i + 1]) * scale,
                dpf2(particles[i + 2]),
                particles[i + 3]
            );
        }
    }

    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;

    @SuppressLint("NotifyDataSetChanged")
    public void toggleTheme() {
        FrameLayout decorView1 = (FrameLayout) getParentActivity().getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView1.getWidth(), decorView1.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        dayNightItem.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        dayNightItem.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        dayNightItem.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + dayNightItem.getMeasuredWidth() / 2f;
        float cy = y + dayNightItem.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) + AndroidUtilities.navigationBarHeight;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                dayNightItem.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                }
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView1.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            isDark = !isDark;
            updateThemeColors();
            setForceDark(isDark, true);
            updateColors();
        });
    }

    @Override
    public boolean isLightStatusBar() {
        if (colorBar == null) {
            return super.isLightStatusBar();
        }
        return ColorUtils.calculateLuminance(colorBar.getColor()) > 0.7f;
    }

    public void updateLightStatusBar() {
        if (getParentActivity() == null) return;
        AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
    }

    private boolean forceDark = isDark;
    public void setForceDark(boolean isDark, boolean playAnimation) {
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            sunDrawable.setCustomEndFrame(isDark ? sunDrawable.getFramesCount() : 0);
            if (sunDrawable != null) {
                sunDrawable.start();
            }
        } else {
            int frame = isDark ? sunDrawable.getFramesCount() - 1 : 0;
            sunDrawable.setCurrentFrame(frame, false, true);
            sunDrawable.setCustomEndFrame(frame);
            if (dayNightItem != null) {
                dayNightItem.invalidate();
            }
        }
    }

    public static class GiftCell extends FrameLayout {

        public long id;
        public TL_stars.starGiftAttributeBackdrop backdrop;
        public TL_stars.starGiftAttributePattern pattern;

        public final FrameLayout card;
        public final GiftSheet.CardBackground cardBackground;
        public final BackupImageView imageView;

        @Nullable
        private final GiftSheet.Ribbon ribbon;

        public GiftCell(Context context, boolean withRibbon, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            card = new FrameLayout(context);
            card.setBackground(cardBackground = new GiftSheet.CardBackground(card, resourcesProvider, false));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            ScaleStateListAnimator.apply(card, 0.025f, 1.25f);

            imageView = new BackupImageView(context);
            card.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER, 0, 12, 0, 12));

            if (withRibbon) {
                ribbon = new GiftSheet.Ribbon(context);
                addView(ribbon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 2, 1, 0));
            } else {
                ribbon = null;
            }
        }

        public void set(int index, TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            final boolean center = index % 3 == 1;
            setPadding(center ? dp(4) : 0, 0, center ? dp(4) : 0, 0);

            setSticker(gift.getDocument(), gift);

            backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            pattern = findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);

            cardBackground.setBackdrop(backdrop);
            cardBackground.setPattern(pattern);
        }

        public void set(int index, TL_stars.SavedStarGift g) {
            id = g.gift.id;
            final boolean center = index % 3 == 1;
            setPadding(center ? dp(4) : 0, 0, center ? dp(4) : 0, 0);

            setSticker(g.gift.getDocument(), g.gift);

            backdrop = findAttribute(g.gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            pattern = findAttribute(g.gift.attributes, TL_stars.starGiftAttributePattern.class);

            cardBackground.setBackdrop(backdrop);
            cardBackground.setPattern(pattern);

            if (ribbon != null) {
                ribbon.setBackdrop(backdrop);
                ribbon.setText(9, "#" + LocaleController.formatNumber(g.gift.num, ','), false);
            }
        }

        public long getGiftId() {
            return id;
        }

        public void setSelected(boolean selected, boolean animated) {
            cardBackground.setSelected(selected, animated);
            final float s = selected ? 0.9f : 1.0f;
            if (animated) {
                imageView.animate().scaleX(s).scaleY(s).start();
            } else {
                imageView.animate().cancel();
                imageView.setScaleX(s);
                imageView.setScaleY(s);
            }
        }

        private TLRPC.Document lastDocument;
        private long lastDocumentId;
        private void setSticker(TLRPC.Document document, Object parentObject) {
            if (document == null) {
                imageView.clearImage();
                lastDocument = null;
                lastDocumentId = 0;
                return;
            }

            if (lastDocument == document) return;
            lastDocument = document;
            lastDocumentId = document.id;

            final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
            final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);

            imageView.setImage(
                ImageLocation.getForDocument(document), "100_100",
                ImageLocation.getForDocument(photoSize, document), "100_100",
                svgThumb,
                parentObject
            );
        }

        public static class Factory extends UItem.UItemFactory<GiftCell> {
            static { setup(new Factory()); }

            @Override
            public GiftCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GiftCell(context, true, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((GiftCell) view).set(-1, (TL_stars.SavedStarGift) item.object);
                ((GiftCell) view).setSelected(item.checked, false);
            }

            public static UItem asGiftCell(TL_stars.SavedStarGift gift) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = gift;
                return item;
            }
        }
    }

    public static boolean eq(TLRPC.EmojiStatus emoji_status, TLRPC.TL_emojiStatusCollectible b) {
        if ((b != null) != (emoji_status instanceof TLRPC.TL_emojiStatusCollectible)) return false;
        if (b == null || !(emoji_status instanceof TLRPC.TL_emojiStatusCollectible)) return false;
        final TLRPC.TL_emojiStatusCollectible a = (TLRPC.TL_emojiStatusCollectible) emoji_status;
        return a.collectible_id == b.collectible_id;
    }

    public static boolean eq(TLRPC.TL_peerColorCollectible a, TLRPC.TL_peerColorCollectible b) {
        if (a == b) return true;
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.collectible_id == b.collectible_id;
    }
}
package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.GradientTools;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.Collections;

public class FeaturesPageView extends BaseListPageView {

    public final static int FEATURES_STORIES = 0;
    public final static int FEATURES_BUSINESS = 1;

    RecyclerListView.SelectionAdapter adapter;
    private final static int VIEW_TYPE_HEADER = 0;
    private final static int VIEW_TYPE_ITEM = 1;
    private final static int VIEW_TYPE_EMPTY = 2;

    ArrayList<Item> items = new ArrayList<>();
    Bitmap bitmap;

    public final int type;

    public FeaturesPageView(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.type = type;
        ArrayList<Item> itemsTmp = new ArrayList<>();

        MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
        SparseIntArray order = null;
        if (type == FEATURES_STORIES) {
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_order,
                    LocaleController.getString(R.string.PremiumStoriesPriority),
                    LocaleController.getString(R.string.PremiumStoriesPriorityDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_PRIORITY_ORDER
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_stealth,
                    LocaleController.getString(R.string.PremiumStoriesStealth),
                    LocaleController.getString(R.string.PremiumStoriesStealthDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_STEALTH_MODE
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_quality_hd,
                    LocaleController.getString(R.string.PremiumStoriesQuality),
                    LocaleController.getString(R.string.PremiumStoriesQualityDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_QUALITY
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_views,
                    LocaleController.getString(R.string.PremiumStoriesViews),
                    LocaleController.getString(R.string.PremiumStoriesViewsDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_VIEWS_HISTORY
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_timer,
                    LocaleController.getString(R.string.PremiumStoriesExpiration),
                    LocaleController.getString(R.string.PremiumStoriesExpirationDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_EXPIRATION_DURATION
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_save,
                    LocaleController.getString(R.string.PremiumStoriesSaveToGallery),
                    LocaleController.getString(R.string.PremiumStoriesSaveToGalleryDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_SAVE_TO_GALLERY
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_caption,
                    LocaleController.getString(R.string.PremiumStoriesCaption),
                    LocaleController.getString(R.string.PremiumStoriesCaptionDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_CAPTION
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.msg_stories_link,
                    LocaleController.getString(R.string.PremiumStoriesFormatting),
                    LocaleController.getString(R.string.PremiumStoriesFormattingDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_STORIES_LINKS_AND_FORMATTING
            ));
        } else if (type == FEATURES_BUSINESS) {
            order = messagesController.businessFeaturesTypesToPosition;
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_premium_location,
                    LocaleController.getString(R.string.PremiumBusinessLocation),
                    LocaleController.getString(R.string.PremiumBusinessLocationDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_LOCATION
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_premium_clock,
                    LocaleController.getString(R.string.PremiumBusinessOpeningHours),
                    LocaleController.getString(R.string.PremiumBusinessOpeningHoursDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_OPENING_HOURS
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_quickreply,
                    LocaleController.getString(R.string.PremiumBusinessQuickReplies),
                    LocaleController.getString(R.string.PremiumBusinessQuickRepliesDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_QUICK_REPLIES
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_feature_status,
                    LocaleController.getString(R.string.PremiumBusinessGreetingMessages),
                    LocaleController.getString(R.string.PremiumBusinessGreetingMessagesDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_GREETING_MESSAGES
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_premium_away,
                    LocaleController.getString(R.string.PremiumBusinessAwayMessages),
                    LocaleController.getString(R.string.PremiumBusinessAwayMessagesDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_AWAY_MESSAGES
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_premium_chatbot,
                    LocaleController.getString(R.string.PremiumBusinessChatbots2),
                    LocaleController.getString(R.string.PremiumBusinessChatbotsDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_CHATBOTS
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_feature_intro,
                    LocaleController.getString(R.string.PremiumBusinessIntro),
                    LocaleController.getString(R.string.PremiumBusinessIntroDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_INTRO
            ));
            itemsTmp.add(new Item(VIEW_TYPE_ITEM, R.drawable.menu_premium_chatlink,
                    LocaleController.getString(R.string.PremiumBusinessChatLinks),
                    LocaleController.getString(R.string.PremiumBusinessChatLinksDescription),
                    PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS_CHAT_LINKS
            ));
        }
        if (order != null) {
            final SparseIntArray finalOrder = order;
//            if (finalOrder.size() > 0) {
//                for (int i = 0; i < itemsTmp.size(); i++) {
//                    if (finalOrder.get(itemsTmp.get(i).order, -1) == -1 && !BuildVars.DEBUG_PRIVATE_VERSION) {
//                        itemsTmp.remove(i);
//                        i--;
//                    }
//                }
//            }
            Collections.sort(itemsTmp, (o1, o2) -> {
                int type1 = finalOrder.get(o1.order, Integer.MAX_VALUE);
                int type2 = finalOrder.get(o2.order, Integer.MAX_VALUE);
                return type1 - type2;
            });
        }

        items.add(new Item(VIEW_TYPE_HEADER));
        items.addAll(itemsTmp);
        items.add(new Item(VIEW_TYPE_EMPTY));
        bitmap = Bitmap.createBitmap(items.size(), 1, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, bitmap.getWidth(), 0, new int[] {
                Theme.getColor(Theme.key_premiumGradient1),
                Theme.getColor(Theme.key_premiumGradient2),
                Theme.getColor(Theme.key_premiumGradient3),
                Theme.getColor(Theme.key_premiumGradient4)
        }, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);
    }

    @Override
    public RecyclerView.Adapter createAdapter() {
        adapter = new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return false;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_TYPE_HEADER) {
                    view = new HeaderView(getContext());
                } else if (viewType == VIEW_TYPE_EMPTY) {
                    view = new FixedHeightEmptyCell(getContext(), 16);
                } else {
                    view = new ItemCell(getContext());
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (items.get(position).viewType == VIEW_TYPE_ITEM) {
                    ItemCell cell = (ItemCell) holder.itemView;
                    cell.imageView.setColorFilter(new PorterDuffColorFilter(bitmap.getPixel(position, 0), PorterDuff.Mode.MULTIPLY));
                    cell.imageView.setImageDrawable(ContextCompat.getDrawable(getContext(), items.get(position).iconRes));
                    cell.textView.setText(items.get(position).text);
                    cell.description.setText(items.get(position).description);
                }
            }

            @Override
            public int getItemViewType(int position) {
                return items.get(position).viewType;
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        return adapter;
    }

    private class Item {
        final int viewType;
        int iconRes;
        String text;
        String description;
        int order;

        private Item(int viewType) {
            this.viewType = viewType;
        }

        public Item(int viewType, int iconRes, String text, String description, int order) {
            this.viewType = viewType;
            this.iconRes = iconRes;
            this.text = text;
            this.description = description;
            this.order = order;
        }
    }

    private class HeaderView extends FrameLayout {

        BackupImageView imageView;
        GradientTools gradientTools = new GradientTools();

        StarParticlesView starParticlesView;
        GLIconTextureView iconTextureView;

        int height;

        public HeaderView(Context context) {
            super(context);
            if (type == FEATURES_STORIES) {
                height = dp(150);

                imageView = new BackupImageView(context);
                imageView.setRoundRadius((int) (dp(65) / 2f));
                addView(imageView, LayoutHelper.createFrame(65, 65, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

                TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(user);
                imageView.getImageReceiver().setForUserOrChat(user, avatarDrawable);

                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                textView.setText(LocaleController.getString(R.string.UpgradedStories));
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 111, 0, 0));

                gradientTools.isLinear = true;
                gradientTools.isDiagonal = true;
                gradientTools.setColors(
                        Theme.getColor(Theme.key_premiumGradient2),
                        Theme.getColor(Theme.key_premiumGradient1)
                );
                gradientTools.paint.setStyle(Paint.Style.STROKE);
                gradientTools.paint.setStrokeCap(Paint.Cap.ROUND);
                gradientTools.paint.setStrokeWidth(AndroidUtilities.dpf2(3.3f));
            } else if (type == FEATURES_BUSINESS) {
                starParticlesView = new StarParticlesView(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        drawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(52));
                    }

                    @Override
                    protected void configure() {
                        drawable.useGradient = true;
                        drawable.useBlur = false;
                        drawable.checkBounds = true;
                        drawable.isCircle = true;
                        drawable.centerOffsetY = dp(-14);
                        drawable.minLifeTime = 2000;
                        drawable.randLifeTime = 3000;
                        drawable.size1 = 16;
                        drawable.useRotate = false;
                        drawable.type = PremiumPreviewFragment.PREMIUM_FEATURE_BUSINESS;
                        drawable.colorKey = Theme.key_premiumGradient2;
                        drawable.init();
                    }
                };
                addView(starParticlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 190, Gravity.TOP | Gravity.FILL_HORIZONTAL));

                iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_COIN) {
                    @Override
                    protected void onAttachedToWindow() {
                        super.onAttachedToWindow();
                        setPaused(false);
                    }

                    @Override
                    protected void onDetachedFromWindow() {
                        super.onDetachedFromWindow();
                        setPaused(true);
                    }
                };
                iconTextureView.setStarParticlesView(starParticlesView);
                Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_premiumGradient2, resourcesProvider), Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.5f));
                iconTextureView.setBackgroundBitmap(bitmap);
//                iconTextureView.mRenderer.forceNight = true;
                iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient2;
                iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient1;
                iconTextureView.mRenderer.updateColors();
                addView(iconTextureView, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL));

                if (iconTextureView != null) {
                    iconTextureView.startEnterAnimation(-360, 100);
                }

                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                textView.setText(LocaleController.getString(R.string.TelegramBusiness));
                textView.setGravity(Gravity.CENTER);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 33, 150, 33, 0));

                textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                textView.setText(LocaleController.getString(R.string.TelegramBusinessSubtitle2));
                textView.setGravity(Gravity.CENTER);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 33, 183, 33, 20));

            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (type == FEATURES_STORIES) {
                imageView.getHitRect(AndroidUtilities.rectTmp2);
                AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                AndroidUtilities.rectTmp.inset(-dp(5), -dp(5));
                gradientTools.setBounds(AndroidUtilities.rectTmp);
                int storiesCount = 7;
                float step = 360 / (float) storiesCount;
                int gapLen = 5;
                for (int i = 0; i < storiesCount; i++) {
                    float startAngle = step * i - 90;
                    float endAngle = startAngle + step;
                    startAngle += gapLen;
                    endAngle -= gapLen;
                    canvas.drawArc(AndroidUtilities.rectTmp, startAngle, (endAngle - startAngle), false, gradientTools.paint);
                }
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, height <= 0 ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    private class ItemCell extends FrameLayout {

        TextView textView;
        TextView description;
        ImageView imageView;

        public ItemCell(Context context) {
            super(context);
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(imageView, LayoutHelper.createFrame(28, 28, 0, 25, 12, 16, 0));

            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 68, 8, 16, 0));

            description = new TextView(context);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(description, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 68, 28, 16, 8));
        }
    }
}

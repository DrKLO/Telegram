package org.telegram.ui.Components.Premium.boosts.adapters;

import static org.telegram.tgnet.TLRPC.TL_payments_checkedGiftCode.NO_USER_ID;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.cells.ActionBtnCell;
import org.telegram.ui.Components.Premium.boosts.cells.HeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.LinkCell;
import org.telegram.ui.Components.Premium.boosts.cells.TableCell;
import org.telegram.ui.Components.Premium.boosts.cells.TextInfoCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.Date;

public abstract class GiftInfoAdapter extends RecyclerListView.SelectionAdapter {

    public static final int
            HOLDER_TYPE_HEADER = 0,
            HOLDER_TYPE_LINK = 1,
            HOLDER_TYPE_TABLE = 2,
            HOLDER_TYPE_TEXT = 3,
            HOLDER_TYPE_BUTTON = 4,
            HOLDER_TYPE_EMPTY = 5;

    private final Theme.ResourcesProvider resourcesProvider;
    private boolean isUnused;
    private BaseFragment baseFragment;
    private TLRPC.TL_payments_checkedGiftCode giftCode;
    private String slug;

    public GiftInfoAdapter(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void init(BaseFragment baseFragment, TLRPC.TL_payments_checkedGiftCode giftCode, String slug) {
        this.isUnused = giftCode.used_date == 0;
        this.baseFragment = baseFragment;
        this.giftCode = giftCode;
        this.slug = slug;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public int getItemViewType(int position) {
        switch (position) {
            case 0:
                return HOLDER_TYPE_HEADER;
            case 1:
                return HOLDER_TYPE_LINK;
            case 2:
                return HOLDER_TYPE_TABLE;
            case 3:
                return HOLDER_TYPE_TEXT;
            case 4:
                return HOLDER_TYPE_BUTTON;
        }
        return HOLDER_TYPE_EMPTY;
    }

    protected abstract void dismiss();

    protected abstract void afterCodeApplied();

    protected abstract void onObjectClicked(TLObject object);

    protected abstract void onHiddenLinkClicked();

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        Context context = parent.getContext();
        switch (viewType) {
            default:
            case HOLDER_TYPE_HEADER:
                view = new HeaderCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_LINK:
                view = new LinkCell(context, baseFragment, resourcesProvider);
                break;
            case HOLDER_TYPE_TABLE:
                view = new TableCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_TEXT:
                view = new TextInfoCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_BUTTON:
                view = new ActionBtnCell(context, resourcesProvider);
                view.setPadding(0,0,0, AndroidUtilities.dp(14));
                break;
            case HOLDER_TYPE_EMPTY:
                view = new View(context);
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final int viewType = holder.getItemViewType();
        switch (viewType) {
            case HOLDER_TYPE_HEADER: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (isUnused) {
                    cell.setGiftLinkText();
                } else {
                    cell.setUsedGiftLinkText();
                }
                if (giftCode.boost != null) {
                    cell.setGiftLinkToUserText(giftCode.to_id, this::onObjectClicked);
                }
                if (giftCode.to_id == NO_USER_ID) {
                    cell.setUnclaimedText();
                }
                break;
            }
            case HOLDER_TYPE_LINK: {
                LinkCell cell = (LinkCell) holder.itemView;
                cell.setSlug(slug);
                if (giftCode.boost != null && slug == null) {
                    cell.hideSlug(this::onHiddenLinkClicked);
                }
                //unclaimed and slug visible only for giveaway creator
                if ((slug == null || slug.isEmpty()) && giftCode.to_id == NO_USER_ID) {
                    cell.hideSlug(this::onHiddenLinkClicked);
                }
                break;
            }
            case HOLDER_TYPE_TABLE: {
                TableCell cell = (TableCell) holder.itemView;
                cell.setData(giftCode, this::onObjectClicked);
                break;
            }
            case HOLDER_TYPE_TEXT: {
                TextInfoCell cell = (TextInfoCell) holder.itemView;
                cell.setTextGravity(Gravity.CENTER);
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                cell.setTopPadding(14);
                cell.setBottomPadding(15);

                if (giftCode.boost != null) {
                    if (slug == null || slug.isEmpty()) {
                        //not activated link
                        cell.setText(LocaleController.getString("BoostingLinkNotActivated", R.string.BoostingLinkNotActivated));
                    } else {
                        //activated link
                        cell.setFixedSize(14);
                        cell.setText(null);
                    }
                    return;
                }

                if (isUnused) {
                    SpannableStringBuilder text = AndroidUtilities.replaceSingleTag(
                            giftCode.to_id == NO_USER_ID ?
                                    LocaleController.getString("BoostingSendLinkToAnyone", R.string.BoostingSendLinkToAnyone)
                                    : LocaleController.getString("BoostingSendLinkToFriends", R.string.BoostingSendLinkToFriends),
                            Theme.key_chat_messageLinkIn, 0,
                            () -> {
                                final String slugLink = "https://t.me/giftcode/" + slug;
                                Bundle args = new Bundle();
                                args.putBoolean("onlySelect", true);
                                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
                                DialogsActivity dialogFragment = new DialogsActivity(args);
                                dialogFragment.setDelegate((fragment1, dids, message, param, topicsFragment) -> {
                                    long did = 0;
                                    for (int a = 0; a < dids.size(); a++) {
                                        did = dids.get(a).dialogId;
                                        baseFragment.getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(slugLink, did, null, null, null, true, null, null, null, true, 0, null, false));
                                    }
                                    fragment1.finishFragment();
                                    BoostDialogs.showGiftLinkForwardedBulletin(did);
                                    return true;
                                });
                                baseFragment.presentFragment(dialogFragment);
                                dismiss();
                            },
                            resourcesProvider
                    );
                    cell.setText(text);
                } else {
                    Date date = new Date(giftCode.used_date * 1000L);
                    String monthTxt = LocaleController.getInstance().formatterYear.format(date);
                    String timeTxt = LocaleController.getInstance().formatterDay.format(date);
                    String fullDateStr = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, monthTxt, timeTxt);
                    cell.setText(LocaleController.formatString("BoostingUsedLinkDate", R.string.BoostingUsedLinkDate, fullDateStr));
                }
                break;
            }
            case HOLDER_TYPE_BUTTON: {
                ActionBtnCell cell = (ActionBtnCell) holder.itemView;
                cell.setOkStyle(isUnused);
                cell.setOnClickListener(v -> {
                    if (isUnused) {
                        if (cell.isLoading()) {
                            return;
                        }
                        cell.updateLoading(true);
                        BoostRepository.applyGiftCode(slug, result -> {
                            cell.updateLoading(false);
                            afterCodeApplied();
                            dismiss();
                        }, error -> {
                            cell.updateLoading(false);
                            BoostDialogs.showToastError(baseFragment.getContext(), error);
                        });
                    } else {
                        dismiss();
                    }
                });
                if (giftCode.boost != null || giftCode.flags == -1) {
                    cell.setCloseStyle();
                    cell.setOnClickListener(v -> dismiss());
                }
                break;
            }
            default: {

            }
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}

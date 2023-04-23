package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.util.ArrayList;
import java.util.Iterator;

import androidx.collection.LongSparseArray;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class CallLogActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

	private ListAdapter listViewAdapter;
	private EmptyTextProgressView emptyView;
	private LinearLayoutManager layoutManager;
	private RecyclerListView listView;
	private ImageView floatingButton;
	private FlickerLoadingView flickerLoadingView;

	private NumberTextView selectedDialogsCountTextView;
	private ArrayList<View> actionModeViews = new ArrayList<>();

	private ActionBarMenuItem otherItem;

	private ArrayList<CallLogRow> calls = new ArrayList<>();
	private boolean loading;
	private boolean firstLoaded;
	private boolean endReached;

	private ArrayList<Long> activeGroupCalls;

	private ArrayList<Integer> selectedIds = new ArrayList<>();

	private int prevPosition;
	private int prevTop;
	private boolean scrollUpdated;
	private boolean floatingHidden;
	private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

	private Drawable greenDrawable;
	private Drawable greenDrawable2;
	private Drawable redDrawable;
	private ImageSpan iconOut, iconIn, iconMissed;
	private TLRPC.User lastCallUser;
	private TLRPC.Chat lastCallChat;

	private Long waitingForCallChatId;

	private boolean openTransitionStarted;

	private static final int TYPE_OUT = 0;
	private static final int TYPE_IN = 1;
	private static final int TYPE_MISSED = 2;

	private static final int delete_all_calls = 1;
	private static final int delete = 2;

	private static class EmptyTextProgressView extends FrameLayout {

		private TextView emptyTextView1;
		private TextView emptyTextView2;
		private View progressView;
		private RLottieImageView imageView;

		public EmptyTextProgressView(Context context) {
			this(context, null);
		}

		public EmptyTextProgressView(Context context, View progressView) {
			super(context);

			addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
			this.progressView = progressView;

			imageView = new RLottieImageView(context);
			imageView.setAnimation(R.raw.utyan_call, 120, 120);
			imageView.setAutoRepeat(false);
			addView(imageView, LayoutHelper.createFrame(140, 140, Gravity.CENTER, 52, 4, 52, 60));
			imageView.setOnClickListener(v -> {
				if (!imageView.isPlaying()) {
					imageView.setProgress(0.0f);
					imageView.playAnimation();
				}
			});

			emptyTextView1 = new TextView(context);
			emptyTextView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
			emptyTextView1.setText(LocaleController.getString("NoRecentCalls", R.string.NoRecentCalls));
			emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
			emptyTextView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
			emptyTextView1.setGravity(Gravity.CENTER);
			addView(emptyTextView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 17, 40, 17, 0));

			emptyTextView2 = new TextView(context);
			String help = LocaleController.getString("NoRecentCallsInfo", R.string.NoRecentCallsInfo);
			if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
				help = help.replace('\n', ' ');
			}
			emptyTextView2.setText(help);
			emptyTextView2.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
			emptyTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			emptyTextView2.setGravity(Gravity.CENTER);
			emptyTextView2.setLineSpacing(AndroidUtilities.dp(2), 1);
			addView(emptyTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 17, 80, 17, 0));

			progressView.setAlpha(0f);
			imageView.setAlpha(0f);
			emptyTextView1.setAlpha(0f);
			emptyTextView2.setAlpha(0f);

			setOnTouchListener((v, event) -> true);
		}

		public void showProgress() {
			imageView.animate().alpha(0f).setDuration(150).start();
			emptyTextView1.animate().alpha(0f).setDuration(150).start();
			emptyTextView2.animate().alpha(0f).setDuration(150).start();
			progressView.animate().alpha(1f).setDuration(150).start();
		}

		public void showTextView() {
			imageView.animate().alpha(1f).setDuration(150).start();
			emptyTextView1.animate().alpha(1f).setDuration(150).start();
			emptyTextView2.animate().alpha(1f).setDuration(150).start();
			progressView.animate().alpha(0f).setDuration(150).start();
			imageView.playAnimation();
		}

		@Override
		public boolean hasOverlappingRendering() {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.didReceiveNewMessages) {
			if (!firstLoaded) {
				return;
			}
			boolean scheduled = (Boolean) args[2];
			if (scheduled) {
				return;
			}
			ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
			for (MessageObject msg : arr) {
				if (msg.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
					long fromId = msg.getFromChatId();
					long userID = fromId == getUserConfig().getClientUserId() ? msg.messageOwner.peer_id.user_id : fromId;
					int callType = fromId == getUserConfig().getClientUserId() ? TYPE_OUT : TYPE_IN;
					TLRPC.PhoneCallDiscardReason reason = msg.messageOwner.action.reason;
					if (callType == TYPE_IN && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
						callType = TYPE_MISSED;
					}
					if (calls.size() > 0) {
						CallLogRow topRow = calls.get(0);
						if (topRow.user.id == userID && topRow.type == callType) {
							topRow.calls.add(0, msg.messageOwner);
							listViewAdapter.notifyItemChanged(0);
							continue;
						}
					}
					CallLogRow row = new CallLogRow();
					row.calls = new ArrayList<>();
					row.calls.add(msg.messageOwner);
					row.user = getMessagesController().getUser(userID);
					row.type = callType;
					row.video = msg.isVideoCall();
					calls.add(0, row);
					listViewAdapter.notifyItemInserted(0);
				}
			}
			if (otherItem != null) {
				otherItem.setVisibility(calls.isEmpty() ? View.GONE : View.VISIBLE);
			}
		} else if (id == NotificationCenter.messagesDeleted) {
			if (!firstLoaded) {
				return;
			}
			boolean scheduled = (Boolean) args[2];
			if (scheduled) {
				return;
			}
			boolean didChange = false;
			ArrayList<Integer> ids = (ArrayList<Integer>) args[0];
			Iterator<CallLogRow> itrtr = calls.iterator();
			while (itrtr.hasNext()) {
				CallLogRow row = itrtr.next();
				Iterator<TLRPC.Message> msgs = row.calls.iterator();
				while (msgs.hasNext()) {
					TLRPC.Message msg = msgs.next();
					if (ids.contains(msg.id)) {
						didChange = true;
						msgs.remove();
					}
				}
				if (row.calls.size() == 0) {
					itrtr.remove();
				}
			}
			if (didChange && listViewAdapter != null) {
				listViewAdapter.notifyDataSetChanged();
			}
		} else if (id == NotificationCenter.activeGroupCallsUpdated) {
			activeGroupCalls = getMessagesController().getActiveGroupCalls();
			if (listViewAdapter != null) {
				listViewAdapter.notifyDataSetChanged();
			}
		} else if (id == NotificationCenter.chatInfoDidLoad) {
			if (waitingForCallChatId == null) {
				return;
			}
			TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
			if (chatFull.id == waitingForCallChatId) {
				ChatObject.Call groupCall = getMessagesController().getGroupCall(waitingForCallChatId, true);
				if (groupCall != null) {
					VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
					waitingForCallChatId = null;
				}
			}
		} else if (id == NotificationCenter.groupCallUpdated) {
			if (waitingForCallChatId == null) {
				return;
			}
			Long chatId = (Long) args[0];
			if (waitingForCallChatId.equals(chatId)) {
				VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
				waitingForCallChatId = null;
			}
		}
	}

	private class CallCell extends FrameLayout {

		private ImageView imageView;
		private ProfileSearchCell profileSearchCell;
		private CheckBox2 checkBox;

		public CallCell(Context context) {
			super(context);

			setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

			profileSearchCell = new ProfileSearchCell(context);
			profileSearchCell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(32) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(32), 0);
			profileSearchCell.setSublabelOffset(AndroidUtilities.dp(LocaleController.isRTL ? 2 : -2), -AndroidUtilities.dp(4));
			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

			imageView = new ImageView(context);
			imageView.setAlpha(214);
			imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.MULTIPLY));
			imageView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
			imageView.setScaleType(ImageView.ScaleType.CENTER);
			imageView.setOnClickListener(v -> {
				CallLogRow row = (CallLogRow) v.getTag();
				TLRPC.UserFull userFull = getMessagesController().getUserFull(row.user.id);
				VoIPHelper.startCall(lastCallUser = row.user, row.video, row.video || userFull != null && userFull.video_calls_available, getParentActivity(), null, getAccountInstance());
			});
			imageView.setContentDescription(LocaleController.getString("Call", R.string.Call));
			addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));

			checkBox = new CheckBox2(context, 21);
			checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
			checkBox.setDrawUnchecked(false);
			checkBox.setDrawBackgroundAsArc(3);
			addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 42, 32, 42, 0));
		}

		public void setChecked(boolean checked, boolean animated) {
			if (checkBox == null) {
				return;
			}
			checkBox.setChecked(checked, animated);
		}
	}

	private class GroupCallCell extends FrameLayout {

		private ProfileSearchCell profileSearchCell;
		private ProgressButton button;
		private TLRPC.Chat currentChat;

		public GroupCallCell(Context context) {
			super(context);

			setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

			String text = LocaleController.getString("VoipChatJoin", R.string.VoipChatJoin);
			button = new ProgressButton(context);
			int width = (int) Math.ceil(button.getPaint().measureText(text));

			profileSearchCell = new ProfileSearchCell(context);
			profileSearchCell.setPadding(LocaleController.isRTL ? (AndroidUtilities.dp(28 + 16) + width) : 0, 0, LocaleController.isRTL ? 0 : (AndroidUtilities.dp(28 + 16) + width), 0);
			profileSearchCell.setSublabelOffset(0, -AndroidUtilities.dp(1));
			addView(profileSearchCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

			button.setText(text);
			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
			button.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
			button.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed), 16);
			button.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
			addView(button, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 16, 14, 0));
			button.setOnClickListener(v -> {
				Long tag = (Long) v.getTag();
				ChatObject.Call call = getMessagesController().getGroupCall(tag, false);
				lastCallChat = getMessagesController().getChat(tag);
				if (call != null) {
					VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
				} else {
					waitingForCallChatId = tag;
					getMessagesController().loadFullChat(tag, 0, true);
				}
			});
		}

		public void setChat(TLRPC.Chat chat) {
			currentChat = chat;
		}
	}

	@Override
	public boolean onFragmentCreate() {
		super.onFragmentCreate();
		getCalls(0, 50);
		activeGroupCalls = getMessagesController().getActiveGroupCalls();

		getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
		getNotificationCenter().addObserver(this, NotificationCenter.messagesDeleted);
		getNotificationCenter().addObserver(this, NotificationCenter.activeGroupCallsUpdated);
		getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
		getNotificationCenter().addObserver(this, NotificationCenter.groupCallUpdated);

		return true;
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
		getNotificationCenter().removeObserver(this, NotificationCenter.messagesDeleted);
		getNotificationCenter().removeObserver(this, NotificationCenter.activeGroupCallsUpdated);
		getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
		getNotificationCenter().removeObserver(this, NotificationCenter.groupCallUpdated);
	}

	@Override
	public View createView(Context context) {
		greenDrawable = getParentActivity().getResources().getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
		greenDrawable.setBounds(0, 0, greenDrawable.getIntrinsicWidth(), greenDrawable.getIntrinsicHeight());
		greenDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_calls_callReceivedGreenIcon), PorterDuff.Mode.MULTIPLY));
		iconOut = new ImageSpan(greenDrawable, ImageSpan.ALIGN_BOTTOM);
		greenDrawable2 = getParentActivity().getResources().getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
		greenDrawable2.setBounds(0, 0, greenDrawable2.getIntrinsicWidth(), greenDrawable2.getIntrinsicHeight());
		greenDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_calls_callReceivedGreenIcon), PorterDuff.Mode.MULTIPLY));
		iconIn = new ImageSpan(greenDrawable2, ImageSpan.ALIGN_BOTTOM);
		redDrawable = getParentActivity().getResources().getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
		redDrawable.setBounds(0, 0, redDrawable.getIntrinsicWidth(), redDrawable.getIntrinsicHeight());
		redDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_fill_RedNormal), PorterDuff.Mode.MULTIPLY));
		iconMissed = new ImageSpan(redDrawable, ImageSpan.ALIGN_BOTTOM);

		actionBar.setBackButtonDrawable(new BackDrawable(false));
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(LocaleController.getString("Calls", R.string.Calls));
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					if (actionBar.isActionModeShowed()) {
						hideActionMode(true);
					} else {
						finishFragment();
					}
				} else if (id == delete_all_calls) {
					showDeleteAlert(true);
				} else if (id == delete) {
					showDeleteAlert(false);
				}
			}
		});

		ActionBarMenu menu = actionBar.createMenu();
		otherItem = menu.addItem(10, R.drawable.ic_ab_other);
		otherItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
		otherItem.addSubItem(delete_all_calls, R.drawable.msg_delete, LocaleController.getString("DeleteAllCalls", R.string.DeleteAllCalls));

		fragmentView = new FrameLayout(context);
		fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
		FrameLayout frameLayout = (FrameLayout) fragmentView;

		flickerLoadingView = new FlickerLoadingView(context);
		flickerLoadingView.setViewType(FlickerLoadingView.CALL_LOG_TYPE);
		flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
		flickerLoadingView.showDate(false);
		emptyView = new EmptyTextProgressView(context, flickerLoadingView);
		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		listView = new RecyclerListView(context);
		listView.setEmptyView(emptyView);
		listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		listView.setAdapter(listViewAdapter = new ListAdapter(context));
		listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

		listView.setOnItemClickListener((view, position) -> {
			if (view instanceof CallCell) {
				CallLogRow row = calls.get(position - listViewAdapter.callsStartRow);
				if (actionBar.isActionModeShowed()) {
					addOrRemoveSelectedDialog(row.calls, (CallCell) view);
				} else {
					Bundle args = new Bundle();
					args.putLong("user_id", row.user.id);
					args.putInt("message_id", row.calls.get(0).id);
					getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
					presentFragment(new ChatActivity(args), true);
				}
			} else if (view instanceof GroupCallCell) {
				GroupCallCell cell = (GroupCallCell) view;
				Bundle args = new Bundle();
				args.putLong("chat_id", cell.currentChat.id);
				getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
				presentFragment(new ChatActivity(args), true);
			}
		});
		listView.setOnItemLongClickListener((view, position) -> {
			if (view instanceof CallCell) {
				addOrRemoveSelectedDialog(calls.get(position - listViewAdapter.callsStartRow).calls, (CallCell) view);
				return true;
			}
			return false;
		});
		listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
				int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
				if (visibleItemCount > 0) {
					int totalItemCount = listViewAdapter.getItemCount();
					if (!endReached && !loading && !calls.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
						final CallLogRow row = calls.get(calls.size() - 1);
						AndroidUtilities.runOnUIThread(() -> getCalls(row.calls.get(row.calls.size() - 1).id, 100));
					}
				}

				if (floatingButton.getVisibility() != View.GONE) {
					final View topChild = recyclerView.getChildAt(0);
					int firstViewTop = 0;
					if (topChild != null) {
						firstViewTop = topChild.getTop();
					}
					boolean goingDown;
					boolean changed = true;
					if (prevPosition == firstVisibleItem) {
						final int topDelta = prevTop - firstViewTop;
						goingDown = firstViewTop < prevTop;
						changed = Math.abs(topDelta) > 1;
					} else {
						goingDown = firstVisibleItem > prevPosition;
					}
					if (changed && scrollUpdated) {
						hideFloatingButton(goingDown);
					}
					prevPosition = firstVisibleItem;
					prevTop = firstViewTop;
					scrollUpdated = true;
				}
			}
		});

		if (loading) {
			emptyView.showProgress();
		} else {
			emptyView.showTextView();
		}

		floatingButton = new ImageView(context);
		floatingButton.setVisibility(View.VISIBLE);
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
		floatingButton.setImageResource(R.drawable.ic_call);
		floatingButton.setContentDescription(LocaleController.getString("Call", R.string.Call));
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
		frameLayout.addView(floatingButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
		floatingButton.setOnClickListener(v -> {
			Bundle args = new Bundle();
			args.putBoolean("destroyAfterSelect", true);
			args.putBoolean("returnAsResult", true);
			args.putBoolean("onlyUsers", true);
			args.putBoolean("allowSelf", false);
			ContactsActivity contactsFragment = new ContactsActivity(args);
			contactsFragment.setDelegate((user, param, activity) -> {
				TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
				VoIPHelper.startCall(lastCallUser = user, false, userFull != null && userFull.video_calls_available, getParentActivity(), null, getAccountInstance());
			});
			presentFragment(contactsFragment);
		});

		return fragmentView;
	}

	private void showDeleteAlert(boolean all) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

		if (all) {
			builder.setTitle(LocaleController.getString("DeleteAllCalls", R.string.DeleteAllCalls));
			builder.setMessage(LocaleController.getString("DeleteAllCallsText", R.string.DeleteAllCallsText));
		} else {
			builder.setTitle(LocaleController.getString("DeleteCalls", R.string.DeleteCalls));
			builder.setMessage(LocaleController.getString("DeleteSelectedCallsText", R.string.DeleteSelectedCallsText));
		}
		final boolean[] checks = new boolean[]{false};
		FrameLayout frameLayout = new FrameLayout(getParentActivity());
		CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
		cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
		cell.setText(LocaleController.getString("DeleteCallsForEveryone", R.string.DeleteCallsForEveryone), "", false, false);
		cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(8) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(8), 0);
		frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 8, 0, 8, 0));
		cell.setOnClickListener(v -> {
			CheckBoxCell cell1 = (CheckBoxCell) v;
			checks[0] = !checks[0];
			cell1.setChecked(checks[0], true);
		});
		builder.setView(frameLayout);
		builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
			if (all) {
				deleteAllMessages(checks[0]);
				calls.clear();
				loading = false;
				endReached = true;
				otherItem.setVisibility(View.GONE);
				listViewAdapter.notifyDataSetChanged();
			} else {
				getMessagesController().deleteMessages(new ArrayList<>(selectedIds), null, null, 0, checks[0], false);
			}
			hideActionMode(false);
		});
		builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
		AlertDialog alertDialog = builder.create();
		showDialog(alertDialog);
		TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (button != null) {
			button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
		}
	}

	private void deleteAllMessages(boolean revoke) {
		TLRPC.TL_messages_deletePhoneCallHistory req = new TLRPC.TL_messages_deletePhoneCallHistory();
		req.revoke = revoke;
		getConnectionsManager().sendRequest(req, (response, error) -> {
			if (response != null) {
				TLRPC.TL_messages_affectedFoundMessages res = (TLRPC.TL_messages_affectedFoundMessages) response;
				TLRPC.TL_updateDeleteMessages updateDeleteMessages = new TLRPC.TL_updateDeleteMessages();
				updateDeleteMessages.messages = res.messages;
				updateDeleteMessages.pts = res.pts;
				updateDeleteMessages.pts_count = res.pts_count;
				final TLRPC.TL_updates updates = new TLRPC.TL_updates();
				updates.updates.add(updateDeleteMessages);
				getMessagesController().processUpdates(updates, false);
				if (res.offset != 0) {
					deleteAllMessages(revoke);
				}
			}
		});
	}

	private void hideActionMode(boolean animated) {
		actionBar.hideActionMode();
		selectedIds.clear();
		for (int a = 0, N = listView.getChildCount(); a < N; a++) {
			View child = listView.getChildAt(a);
			if (child instanceof CallCell) {
				((CallCell) child).setChecked(false, animated);
			}
		}
	}

	private boolean isSelected(ArrayList<TLRPC.Message> messages) {
		for (int a = 0, N = messages.size(); a < N; a++) {
			if (selectedIds.contains(messages.get(a).id)) {
				return true;
			}
		}
		return false;
	}

	private void createActionMode() {
		if (actionBar.actionModeIsExist(null)) {
			return;
		}
		final ActionBarMenu actionMode = actionBar.createActionMode();

		selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
		selectedDialogsCountTextView.setTextSize(18);
		selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
		selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
		actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
		selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

		actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete)));
	}

	private boolean addOrRemoveSelectedDialog(ArrayList<TLRPC.Message> messages, CallCell cell) {
		if (messages.isEmpty()) {
			return false;
		}
		if (isSelected(messages)) {
			for (int a = 0, N = messages.size(); a < N; a++) {
				selectedIds.remove((Integer) messages.get(a).id);
			}
			cell.setChecked(false, true);
			showOrUpdateActionMode();
			return false;
		} else {
			for (int a = 0, N = messages.size(); a < N; a++) {
				Integer id = messages.get(a).id;
				if (!selectedIds.contains(id)) {
					selectedIds.add(id);
				}
			}
			cell.setChecked(true, true);
			showOrUpdateActionMode();
			return true;
		}
	}

	private void showOrUpdateActionMode() {
		boolean updateAnimated = false;
		if (actionBar.isActionModeShowed()) {
			if (selectedIds.isEmpty()) {
				hideActionMode(true);
				return;
			}
			updateAnimated = true;
		} else {
			createActionMode();
			actionBar.showActionMode();

			AnimatorSet animatorSet = new AnimatorSet();
			ArrayList<Animator> animators = new ArrayList<>();
			for (int a = 0; a < actionModeViews.size(); a++) {
				View view = actionModeViews.get(a);
				view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
				AndroidUtilities.clearDrawableAnimation(view);
				animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
			}
			animatorSet.playTogether(animators);
			animatorSet.setDuration(200);
			animatorSet.start();
		}
		selectedDialogsCountTextView.setNumber(selectedIds.size(), updateAnimated);
	}

	private void hideFloatingButton(boolean hide) {
		if (floatingHidden == hide) {
			return;
		}
		floatingHidden = hide;
		ObjectAnimator animator = ObjectAnimator.ofFloat(floatingButton, "translationY", floatingHidden ? AndroidUtilities.dp(100) : 0).setDuration(300);
		animator.setInterpolator(floatingInterpolator);
		floatingButton.setClickable(!hide);
		animator.start();
	}

	private void getCalls(int max_id, final int count) {
		if (loading) {
			return;
		}
		loading = true;
		if (emptyView != null && !firstLoaded) {
			emptyView.showProgress();
		}
		if (listViewAdapter != null) {
			listViewAdapter.notifyDataSetChanged();
		}
		TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
		req.limit = count;
		req.peer = new TLRPC.TL_inputPeerEmpty();
		req.filter = new TLRPC.TL_inputMessagesFilterPhoneCalls();
		req.q = "";
		req.offset_id = max_id;
		int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			int oldCount = Math.max(listViewAdapter.callsStartRow, 0) + calls.size();
			if (error == null) {
				LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
				TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) response;
				endReached = msgs.messages.isEmpty();
				for (int a = 0; a < msgs.users.size(); a++) {
					TLRPC.User user = msgs.users.get(a);
					users.put(user.id, user);
				}
				CallLogRow currentRow = calls.size() > 0 ? calls.get(calls.size() - 1) : null;
				for (int a = 0; a < msgs.messages.size(); a++) {
					TLRPC.Message msg = msgs.messages.get(a);
					if (msg.action == null || msg.action instanceof TLRPC.TL_messageActionHistoryClear) {
						continue;
					}
					int callType = MessageObject.getFromChatId(msg) == getUserConfig().getClientUserId() ? TYPE_OUT : TYPE_IN;
					TLRPC.PhoneCallDiscardReason reason = msg.action.reason;
					if (callType == TYPE_IN && (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy)) {
						callType = TYPE_MISSED;
					}

					long fromId = MessageObject.getFromChatId(msg);
					long userID = fromId == getUserConfig().getClientUserId() ? msg.peer_id.user_id : fromId;
					if (currentRow == null || currentRow.user.id != userID || currentRow.type != callType) {
						if (currentRow != null && !calls.contains(currentRow)) {
							calls.add(currentRow);
						}
						CallLogRow row = new CallLogRow();
						row.calls = new ArrayList<>();
						row.user = users.get(userID);
						row.type = callType;
						row.video = msg.action != null && msg.action.video;
						currentRow = row;
					}
					currentRow.calls.add(msg);
				}
				if (currentRow != null && currentRow.calls.size() > 0 && !calls.contains(currentRow)) {
					calls.add(currentRow);
				}
			} else {
				endReached = true;
			}
			loading = false;
			showItemsAnimated(oldCount);
			if (!firstLoaded) {
				resumeDelayedFragmentAnimation();
			}
			firstLoaded = true;
			otherItem.setVisibility(calls.isEmpty() ? View.GONE : View.VISIBLE);
			if (emptyView != null) {
				emptyView.showTextView();
			}
			if (listViewAdapter != null) {
				listViewAdapter.notifyDataSetChanged();
			}
		}), ConnectionsManager.RequestFlagFailOnServerErrors);
		getConnectionsManager().bindRequestToGuid(reqId, classGuid);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listViewAdapter != null) {
			listViewAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 101 || requestCode == 102 || requestCode == 103) {
			boolean allGranted = true;
			for (int a = 0; a < grantResults.length; a++) {
				if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
			if (grantResults.length > 0 && allGranted) {
				if (requestCode == 103) {
					VoIPHelper.startCall(lastCallChat, null, null, false, getParentActivity(), CallLogActivity.this, getAccountInstance());
				} else {
					TLRPC.UserFull userFull = lastCallUser != null ? getMessagesController().getUserFull(lastCallUser.id) : null;
					VoIPHelper.startCall(lastCallUser, requestCode == 102, requestCode == 102 || userFull != null && userFull.video_calls_available, getParentActivity(), null, getAccountInstance());
				}
			} else {
				VoIPHelper.permissionDenied(getParentActivity(), null, requestCode);
			}
		}
	}

	private class ListAdapter extends RecyclerListView.SelectionAdapter {

		private Context mContext;
		private int activeHeaderRow;
		private int callsHeaderRow;
		private int activeStartRow;
		private int activeEndRow;
		private int callsStartRow;
		private int callsEndRow;
		private int loadingCallsRow;
		private int sectionRow;
		private int rowsCount;

		public ListAdapter(Context context) {
			mContext = context;
		}

		private void updateRows() {
			activeHeaderRow = -1;
			callsHeaderRow = -1;
			activeStartRow = -1;
			activeEndRow = -1;
			callsStartRow = -1;
			callsEndRow = -1;
			loadingCallsRow = -1;
			sectionRow = -1;
			rowsCount = 0;

			if (!activeGroupCalls.isEmpty()) {
				activeHeaderRow = rowsCount++;
				activeStartRow = rowsCount;
				rowsCount += activeGroupCalls.size();
				activeEndRow = rowsCount;
			}
			if (!calls.isEmpty()) {
				if (activeHeaderRow != -1) {
					sectionRow = rowsCount++;
					callsHeaderRow = rowsCount++;
				}
				callsStartRow = rowsCount;
				rowsCount += calls.size();
				callsEndRow = rowsCount;
				if (!endReached) {
					loadingCallsRow = rowsCount++;
				}
			}
		}

		@Override
		public void notifyDataSetChanged() {
			updateRows();
			super.notifyDataSetChanged();
		}

		@Override
		public void notifyItemChanged(int position) {
			updateRows();
			super.notifyItemChanged(position);
		}

		@Override
		public void notifyItemChanged(int position, @Nullable Object payload) {
			updateRows();
			super.notifyItemChanged(position, payload);
		}

		@Override
		public void notifyItemRangeChanged(int positionStart, int itemCount) {
			updateRows();
			super.notifyItemRangeChanged(positionStart, itemCount);
		}

		@Override
		public void notifyItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
			updateRows();
			super.notifyItemRangeChanged(positionStart, itemCount, payload);
		}

		@Override
		public void notifyItemInserted(int position) {
			updateRows();
			super.notifyItemInserted(position);
		}

		@Override
		public void notifyItemMoved(int fromPosition, int toPosition) {
			updateRows();
			super.notifyItemMoved(fromPosition, toPosition);
		}

		@Override
		public void notifyItemRangeInserted(int positionStart, int itemCount) {
			updateRows();
			super.notifyItemRangeInserted(positionStart, itemCount);
		}

		@Override
		public void notifyItemRemoved(int position) {
			updateRows();
			super.notifyItemRemoved(position);
		}

		@Override
		public void notifyItemRangeRemoved(int positionStart, int itemCount) {
			updateRows();
			super.notifyItemRangeRemoved(positionStart, itemCount);
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			int type = holder.getItemViewType();
			return type == 0 || type == 4;
		}

		@Override
		public int getItemCount() {
			return rowsCount;
		}

		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view;
			switch (viewType) {
				case 0:
					view = new CallCell(mContext);
					break;
				case 1:
					FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
					flickerLoadingView.setIsSingleCell(true);
					flickerLoadingView.setViewType(FlickerLoadingView.CALL_LOG_TYPE);
					flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
					flickerLoadingView.showDate(false);
					view = flickerLoadingView;
					break;
				case 2:
					view = new TextInfoPrivacyCell(mContext);
					view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
					break;
				case 3:
					view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, 2, false, getResourceProvider());
					view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
					break;
				case 4:
					view = new GroupCallCell(mContext);
					break;
				case 5:
				default:
					view = new ShadowSectionCell(mContext);
			}
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
			if (holder.itemView instanceof CallCell) {
				CallLogRow row = calls.get(holder.getAdapterPosition() - callsStartRow);
				((CallCell) holder.itemView).setChecked(isSelected(row.calls), false);
			}
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 0: {
					position -= callsStartRow;
					CallLogRow row = calls.get(position);

					CallCell cell = (CallCell) holder.itemView;
					cell.imageView.setImageResource(row.video ? R.drawable.profile_video : R.drawable.profile_phone);
					TLRPC.Message last = row.calls.get(0);
					SpannableString subtitle;
					String ldir = LocaleController.isRTL ? "\u202b" : "";
					if (row.calls.size() == 1) {
						subtitle = new SpannableString(ldir + "  " + LocaleController.formatDateCallLog(last.date));
					} else {
						subtitle = new SpannableString(String.format(ldir + "  (%d) %s", row.calls.size(), LocaleController.formatDateCallLog(last.date)));
					}
					switch (row.type) {
						case TYPE_OUT:
							subtitle.setSpan(iconOut, ldir.length(), ldir.length() + 1, 0);
							//cell.setContentDescription(LocaleController.getString("CallMessageOutgoing", R.string.CallMessageOutgoing));
							break;
						case TYPE_IN:
							subtitle.setSpan(iconIn, ldir.length(), ldir.length() + 1, 0);
							//cell.setContentDescription(LocaleController.getString("CallMessageIncoming", R.string.CallMessageIncoming));
							break;
						case TYPE_MISSED:
							subtitle.setSpan(iconMissed, ldir.length(), ldir.length() + 1, 0);
							//cell.setContentDescription(LocaleController.getString("CallMessageIncomingMissed", R.string.CallMessageIncomingMissed));
							break;
					}
					cell.profileSearchCell.setData(row.user, null, null, subtitle, false, false);
					cell.profileSearchCell.useSeparator = position != calls.size() - 1 || !endReached;
					cell.imageView.setTag(row);
					break;
				}
				case 3: {
					HeaderCell cell = (HeaderCell) holder.itemView;
					if (position == activeHeaderRow) {
						cell.setText(LocaleController.getString("VoipChatActiveChats", R.string.VoipChatActiveChats));
					} else if (position == callsHeaderRow) {
						cell.setText(LocaleController.getString("VoipChatRecentCalls", R.string.VoipChatRecentCalls));
					}
					break;
				}
				case 4: {
					position -= activeStartRow;
					Long chatId = activeGroupCalls.get(position);
					TLRPC.Chat chat = getMessagesController().getChat(chatId);
					GroupCallCell cell = (GroupCallCell) holder.itemView;
					cell.setChat(chat);
					cell.button.setTag(chat.id);
					String text;
					if (ChatObject.isChannel(chat) && !chat.megagroup) {
						if (!ChatObject.isPublic(chat)) {
							text = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
						} else {
							text = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
						}
					} else {
						if (chat.has_geo) {
							text = LocaleController.getString("MegaLocation", R.string.MegaLocation);
						} else if (!ChatObject.isPublic(chat)) {
							text = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
						} else {
							text = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
						}
					}
					cell.profileSearchCell.useSeparator = position != activeGroupCalls.size() - 1 && !endReached;
					cell.profileSearchCell.setData(chat, null, null, text, false, false);
					break;
				}
			}
		}

		@Override
		public int getItemViewType(int i) {
			if (i == activeHeaderRow || i == callsHeaderRow) {
				return 3;
			} else if (i >= callsStartRow && i < callsEndRow) {
				return 0;
			} else if (i >= activeStartRow && i < activeEndRow) {
				return 4;
			} else if (i == loadingCallsRow) {
				return 1;
			} else if (i == sectionRow) {
				return 5;
			}
			return 2;
		}
	}

	private static class CallLogRow {
		public TLRPC.User user;
		public ArrayList<TLRPC.Message> calls;
		public int type;
		public boolean video;
	}

	@Override
	public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
		super.onTransitionAnimationStart(isOpen, backward);
		if (isOpen) {
			openTransitionStarted = true;
		}
	}

	@Override
	public boolean needDelayOpenAnimation() {
		return true;
	}

	private void showItemsAnimated(int from) {
		if (isPaused || !openTransitionStarted) {
			return;
		}
		View progressView = null;
		for (int i = 0; i < listView.getChildCount(); i++) {
			View child = listView.getChildAt(i);
			if (child instanceof FlickerLoadingView) {
				progressView = child;
			}
		}
		final View finalProgressView = progressView;
		if (progressView != null) {
			listView.removeView(progressView);
		}

		listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				listView.getViewTreeObserver().removeOnPreDrawListener(this);
				int n = listView.getChildCount();
				AnimatorSet animatorSet = new AnimatorSet();
				for (int i = 0; i < n; i++) {
					View child = listView.getChildAt(i);
					RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
					if (child == finalProgressView || listView.getChildAdapterPosition(child) < from || child instanceof GroupCallCell || child instanceof HeaderCell && holder.getAdapterPosition() == listViewAdapter.activeHeaderRow) {
						continue;
					}
					child.setAlpha(0);
					int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
					int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
					ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
					a.setStartDelay(delay);
					a.setDuration(200);
					animatorSet.playTogether(a);
				}

				if (finalProgressView != null && finalProgressView.getParent() == null) {
					listView.addView(finalProgressView);
					RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
					if (layoutManager != null) {
						layoutManager.ignoreView(finalProgressView);
						Animator animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0);
						animator.addListener(new AnimatorListenerAdapter() {
							@Override
							public void onAnimationEnd(Animator animation) {
								finalProgressView.setAlpha(1f);
								layoutManager.stopIgnoringView(finalProgressView);
								listView.removeView(finalProgressView);
							}
						});
						animator.start();
					}
				}

				animatorSet.start();
				return true;
			}
		});
	}

	@Override
	public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
			if (listView != null) {
				int count = listView.getChildCount();
				for (int a = 0; a < count; a++) {
					View child = listView.getChildAt(a);
					if (child instanceof CallCell) {
						CallCell cell = (CallCell) child;
						cell.profileSearchCell.update(0);
					}
				}
			}
		};


		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{LocationCell.class, CallCell.class, HeaderCell.class, GroupCallCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
		themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

		themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyTextProgressView.class}, new String[]{"emptyTextView1"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
		themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EmptyTextProgressView.class}, new String[]{"emptyTextView2"}, null, null, null, Theme.key_emptyListPlaceholder));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

		themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
		themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
		themeDescriptions.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_featuredStickers_addButton));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Paint[]{Theme.dialogs_namePaint[0], Theme.dialogs_namePaint[1], Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint[0], Theme.dialogs_nameEncryptedPaint[1], Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CallCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
		themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{greenDrawable, greenDrawable2, Theme.calllog_msgCallUpRedDrawable, Theme.calllog_msgCallDownRedDrawable}, null, Theme.key_calls_callReceivedGreenIcon));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{redDrawable, Theme.calllog_msgCallUpGreenDrawable, Theme.calllog_msgCallDownGreenDrawable}, null, Theme.key_fill_RedNormal));
		themeDescriptions.add(new ThemeDescription(flickerLoadingView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

		return themeDescriptions;
	}
}

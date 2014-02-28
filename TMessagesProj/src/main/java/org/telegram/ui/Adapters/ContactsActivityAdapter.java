/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatOrUserCell;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.util.ArrayList;
import java.util.HashMap;

public class ContactsActivityAdapter extends SectionedBaseAdapter {
    private Context mContext;
    private boolean onlyUsers;
    private boolean usersAsSections;
    private HashMap<Integer, TLRPC.User> ignoreUsers;

    public ContactsActivityAdapter(Context context, boolean arg1, boolean arg2, HashMap<Integer, TLRPC.User> arg3) {
        mContext = context;
        onlyUsers = arg1;
        usersAsSections = arg2;
        ignoreUsers = arg3;
    }

    @Override
    public Object getItem(int section, int position) {
        return null;
    }

    @Override
    public long getItemId(int section, int position) {
        return 0;
    }

    @Override
    public int getSectionCount() {
        int count = 0;
        if (usersAsSections) {
            count += ContactsController.Instance.sortedUsersSectionsArray.size();
        } else {
            count++;
        }
        if (!onlyUsers) {
            count += ContactsController.Instance.sortedContactsSectionsArray.size();
        }
        return count;
    }

    @Override
    public int getCountForSection(int section) {
        if (usersAsSections) {
            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                return arr.size();
            }
        } else {
            if (section == 0) {
                return ContactsController.Instance.contacts.size() + 1;
            }
        }
        ArrayList<ContactsController.Contact> arr = ContactsController.Instance.contactsSectionsDict.get(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
        return arr.size();
    }

    @Override
    public View getItemView(int section, int position, View convertView, ViewGroup parent) {

        TLRPC.User user = null;
        int count = 0;
        if (usersAsSections) {
            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = ContactsController.Instance.usersSectionsDict.get(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                user = MessagesController.Instance.users.get(arr.get(position).user_id);
                count = arr.size();
            }
        } else {
            if (section == 0) {
                if (position == 0) {
                    if (convertView == null) {
                        LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        convertView = li.inflate(R.layout.contacts_invite_row_layout, parent, false);
                    }
                    View divider = convertView.findViewById(R.id.settings_row_divider);
                    if (ContactsController.Instance.contacts.isEmpty()) {
                        divider.setVisibility(View.INVISIBLE);
                    } else {
                        divider.setVisibility(View.VISIBLE);
                    }
                    return convertView;
                }
                user = MessagesController.Instance.users.get(ContactsController.Instance.contacts.get(position - 1).user_id);
                count = ContactsController.Instance.contacts.size();
            }
        }
        if (user != null) {
            if (convertView == null) {
                convertView = new ChatOrUserCell(mContext);
                ((ChatOrUserCell)convertView).useBoldFont = true;
                ((ChatOrUserCell)convertView).usePadding = false;
            }

            ((ChatOrUserCell)convertView).setData(user, null, null, null, null);

            if (ignoreUsers != null) {
                if (ignoreUsers.containsKey(user.id)) {
                    ((ChatOrUserCell)convertView).drawAlpha = 0.5f;
                } else {
                    ((ChatOrUserCell)convertView).drawAlpha = 1.0f;
                }
            }

            ((ChatOrUserCell) convertView).useSeparator = position != count - 1;

            return convertView;
        }

        TextView textView;
        if (convertView == null) {
            LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = li.inflate(R.layout.settings_row_button_layout, parent, false);
            textView = (TextView)convertView.findViewById(R.id.settings_row_text);
        } else {
            textView = (TextView)convertView.findViewById(R.id.settings_row_text);
        }

        View divider = convertView.findViewById(R.id.settings_row_divider);
        ArrayList<ContactsController.Contact> arr = ContactsController.Instance.contactsSectionsDict.get(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
        ContactsController.Contact contact = arr.get(position);
        if (divider != null) {
            if (position == arr.size() - 1) {
                divider.setVisibility(View.INVISIBLE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }
        }
        if (contact.first_name != null && contact.last_name != null) {
            textView.setText(Html.fromHtml(contact.first_name + " <b>" + contact.last_name + "</b>"));
        } else if (contact.first_name != null && contact.last_name == null) {
            textView.setText(Html.fromHtml("<b>" + contact.first_name + "</b>"));
        } else {
            textView.setText(Html.fromHtml("<b>" + contact.last_name + "</b>"));
        }
        return convertView;
    }

    @Override
    public int getItemViewType(int section, int position) {
        if (usersAsSections) {
            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                return 0;
            }
        } else if (section == 0) {
            if (position == 0) {
                return 2;
            }
            return 0;
        }
        return 1;
    }

    @Override
    public int getItemViewTypeCount() {
        return 3;
    }

    @Override
    public int getSectionHeaderViewType(int section) {
        if (usersAsSections) {
            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                return 1;
            }
        } else if (section == 0) {
            return 0;
        }
        return 1;
    }

    @Override
    public int getSectionHeaderViewTypeCount() {
        return 2;
    }

    @Override
    public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
        if (usersAsSections) {
            if (section < ContactsController.Instance.sortedUsersSectionsArray.size()) {
                if (convertView == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                    convertView.setBackgroundColor(0xffffffff);
                }
                TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
                textView.setText(ContactsController.Instance.sortedUsersSectionsArray.get(section));
                return convertView;
            }
        } else {
            if (section == 0) {
                if (convertView == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = li.inflate(R.layout.empty_layout, parent, false);
                }
                return convertView;
            }
        }

        if (convertView == null) {
            LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = li.inflate(R.layout.settings_section_layout, parent, false);
            convertView.setBackgroundColor(0xffffffff);
        }
        TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
        textView.setText(ContactsController.Instance.sortedContactsSectionsArray.get(section - 1));
        return convertView;
    }
}

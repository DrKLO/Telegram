/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.util.Log;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.TLRPC;

public class UserObject {

    public static boolean isDeleted(TLRPC.User user) {
        return user == null || user instanceof TLRPC.TL_userDeleted_old2 || user instanceof TLRPC.TL_userEmpty || user.deleted;
    }

    public static boolean isContact(TLRPC.User user) {
        return user instanceof TLRPC.TL_userContact_old2 || user.contact || user.mutual_contact;
    }

    public static boolean isUserSelf(TLRPC.User user) {
        return user instanceof TLRPC.TL_userSelf_old3 || user.self;
    }

    public static String getUserName(TLRPC.User user) {
        if (user == null || isDeleted(user)) {
            return LocaleController.getString("HiddenName", R.string.HiddenName);
        }
        String name = ContactsController.formatName(user.first_name, user.last_name);
        return name.length() != 0 || user.phone == null || user.phone.length() == 0 ? name : PhoneFormat.getInstance().format("+" + user.phone);
    }

    public static String getFirstName(TLRPC.User user) {
        if (user == null || isDeleted(user)) {
            return "DELETED";
        }
        String name = user.first_name;
        if (name == null || name.length() == 0) {
            name = user.last_name;
        }
        return name != null && name.length() > 0 ? name : LocaleController.getString("HiddenName", R.string.HiddenName);
    }

    /*private static final int lastSize = 150;

    public static String getLastName(String last) {
        if (last == null || last.length() == 0) {
            return "";
        }
        //int lastSize = 260;
        if(last.length() > lastSize && last.charAt(last.length() -1) == '\u00A0') {
            //Log.e("UserObject","getLastName YES " + last + " : " + last.length());
            last = last.substring(0, lastSize - 1);
            //last = last.substring(0, lastSize - 2);
            last = last.replaceAll("\\s+$", "");
            //last = last.replaceAll(" +$", "");
        }
        return last;
    }

    public static String getUserStatus(String status) {
        if (status == null || status.length() == 0) {
            return "";
        }
        //int lastSize = 253;
        if(status.length() > lastSize && status.charAt(status.length() -1) == '\u00A0') {
            //Log.e("UserObject","getUserStatus YES " + status.length());
            status = status.substring(lastSize, status.length() - 1);
            status = status.replaceAll("\\s+$", "");
            //status = status.replaceAll(" +$", "");
            return status;
        }
        //Log.e("UserObject","getUserStatus NO "+ status + " / "+ status.length());
        return null;
    }

    public static String setStatus(String newLast, String newStatus){
        //int lastSize = 253;
        StringBuilder buffer = new StringBuilder(lastSize + newStatus.length());
        //char c = '\n';
        try{
            int x = 0;
            if(newLast.length() == 0){
                buffer.append('\u00A0'); //Ok, malo en pc
                buffer.append('\n');
                //buffer.append(' ');
                x = 2;
            }
            for (int i = x; i < lastSize + newStatus.length(); i++){
                if(i < lastSize){// 0 99
                    //buffer.append(newLast.length() > i ? newLast.charAt(i) : " ");
                    buffer.append(newLast.length() > i ? newLast.charAt(i) : newLast.length() == i ? '\n' : '\t');
                }
                else{ // 100
                    buffer.append(newStatus.length() > i - lastSize ? newStatus.charAt(i - lastSize) : " ");
                }
            }
            buffer.append('\u00A0');
            return buffer.toString();
        } catch(Exception e) {
            FileLog.e("ChangeName", e.toString());
        }
        return newLast;
    }*/

}

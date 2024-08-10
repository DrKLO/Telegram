package org.xatirchi.utils

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import javax.inject.Singleton

@Singleton
object LanguageCode {
    var languageCode = "en"

    var languages = ArrayList<Language>()
    var titlesLanguages = ArrayList<TitleLanguages>()

    private var sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private var editor = sharedPreferences.edit()

    init {
        languages.add(Language("English", "en-EN"))
        languages.add(Language("Uzbek", "uz-UZ"))
        languages.add(Language("Russian", "ru-RU"))
        languages.add(Language("العربية", "ar-AR"))
        languages.add(Language("Belarusian ", "by-BY"))
        languages.add(Language("Catalan ", "ca-CA"))
        languages.add(Language("Croatian ", "hr-HR"))
        languages.add(Language("Czech ", "cs-CS"))
        languages.add(Language("Nederlands-Dutch ", "nl-NL"))
        languages.add(Language("Finnish ", "fi-FI"))
        languages.add(Language("French ", "fr-FR"))
        languages.add(Language("German", "de-DE"))
        languages.add(Language("Hebrew", "he-HE"))
        languages.add(Language("Hungarian", "hu-HU"))
        languages.add(Language("Indonesian", "in-IN"))
        languages.add(Language("Indonesian", "in-IN"))
        languages.add(Language("Italiano", "it-IT"))
        languages.add(Language("Kazakh", "kz-KZ"))
        languages.add(Language("한국어", "ko-KO"))
        languages.add(Language("Malay", "ms-MS"))
        languages.add(Language("Norwegian", "no-NO"))
        languages.add(Language("فارسی", "fa-IR"))
        languages.add(Language("Polish", "pl-PL"))
        languages.add(Language("Português (Brasil)", "pt-PT"))
        languages.add(Language("Serbian", "sr-SR"))
        languages.add(Language("Slovak", "sl-SL"))
        languages.add(Language("Español", "es-ES"))
        languages.add(Language("Swedish", "sv-SV"))
        languages.add(Language("Turkish", "th-TH"))
        languages.add(Language("Ukrainian", "uk-UK"))

//        ----------------------------------------------

        titlesLanguages.add(TitleLanguages(0,"Speak something...","Biror nima gapiring...","Скажи что-нибудь..."))
        titlesLanguages.add(TitleLanguages(1,"Xatirchi settings","Xatirchi Sozlamalar","Xatirchi Настройки"))
        titlesLanguages.add(TitleLanguages(2,"2FA password","Ikki bosqichli tasdiqlash paroli","Пароль двухфакторной аутентификации"))
        titlesLanguages.add(TitleLanguages(3,"enter 2FA password","Ikki bosqichli tasdiqlash parolini kiriting","Введите пароль двухфакторной аутентификации\n"))
        titlesLanguages.add(TitleLanguages(4,"next","Keyingi","Далее"))
        titlesLanguages.add(TitleLanguages(5,"Set up a 2FA security key to recover passwords when forgotten","Parol esdan chiqqanda ularni qayta tiklash uchun 2FA xavfsizlik kalitini o'rnating","Установите ключ безопасности 2FA для восстановления паролей в случае их утери"))
        titlesLanguages.add(TitleLanguages(6,"change","O'zgartirish","Изменить"))
        titlesLanguages.add(TitleLanguages(7,"save","Saqlash","Сохранить"))
        titlesLanguages.add(TitleLanguages(8,"password","Parol","Пароль"))
        titlesLanguages.add(TitleLanguages(9,"Change password","Parolni o'zgartirish","Изменить пароль"))
        titlesLanguages.add(TitleLanguages(10,"Remove password","Parolni o'chirish","Удалить пароль"))
        titlesLanguages.add(TitleLanguages(11,"Enter current password","Joriy parolni kiriting","Введите текущий пароль"))
        titlesLanguages.add(TitleLanguages(12,"Enter the access code for User1! Note: This password is only valid for User1","User1 uchun kirish kodini kiriting! Eslatma: Ushbu parol faqat User1 uchun amal qiladi","Введите код доступа для User1! Примечание: этот пароль действителен только для User1"))
        titlesLanguages.add(TitleLanguages(13,"Enter new password","Yangi parolni kiriting","Введите новый пароль"))
        titlesLanguages.add(TitleLanguages(14,"Select language","Tilni tanlang","Выберите язык"))
        titlesLanguages.add(TitleLanguages(15,"Save all messages","Barcha xabarlarni saqlash","Сохранить все сообщения"))
        titlesLanguages.add(TitleLanguages(16,"Messages deleted from both sides are also saved by me. (Deleted from both sides)","2 tarafdan o'chirilgan xabarlar ham menda saqlanadi. (2-tarafdan o'chib ketadi)","Сообщения, удалённые с обеих сторон, также сохраняются у меня. (Удаляются с обеих сторон)"))
        titlesLanguages.add(TitleLanguages(17,"Save messages deleted by me","Men o'chirgan xabarlarni saqlash","Сохранять сообщения, удаленные мной"))
        titlesLanguages.add(TitleLanguages(18,"Meaning, my messages are deleted from both sides but remain saved on my side.","Ya'ni, meni xabarlarim 2-tarafdan o'chib ketadi, ammo o'zimda saqlanib qoladi.","То есть, мои сообщения удаляются с обеих сторон, но остаются сохраненными у меня."))
        titlesLanguages.add(TitleLanguages(19,"Save deleted messages","O'chirilgan xabarlarni saqlash","Сохранять удалённые сообщения"))
        titlesLanguages.add(TitleLanguages(20,"Save messages that are deleted from both sides on my end.","2-tarafdan o'chirilgan xabarlarni menda saqlash.","Сохранять у себя сообщения, удалённые с обеих сторон."))
        titlesLanguages.add(TitleLanguages(21,"As usual","Odatdagidek","Как обычно"))
        titlesLanguages.add(TitleLanguages(22,"Works the same as the original Telegram.","Orginal Telegram bilan bir xil ishlaydi","Работает так же, как оригинальный Telegram.\n"))
        titlesLanguages.add(TitleLanguages(23,"Save edited messages off","Tahrirlangan xabarlarni saqlashni o'chirish","Отключить сохранение отредактированных сообщений"))
        titlesLanguages.add(TitleLanguages(24,"Save edited messages on","Tahrirlangan xabarlarni saqlashni yoqish","Включить сохранение отредактированных сообщений"))
        titlesLanguages.add(TitleLanguages(25,"Ghost on","Ghost yoqilgan","Гост-режим включен"))
        titlesLanguages.add(TitleLanguages(26,"Ghost off","Ghost o'chirilgan","Гост-режим выключен"))
        titlesLanguages.add(TitleLanguages(27,"Ghost","Ghost","Гост"))
        titlesLanguages.add(TitleLanguages(28,"2FA password reset","Ikki bosqichli tasdiqlash parolini tiklash","Сброс пароля двухфакторной аутентификации"))
        titlesLanguages.add(TitleLanguages(29,"Language menu","Til menyusi","Меню языков"))
        titlesLanguages.add(TitleLanguages(30,"Save edited messages","Tahrirlangan xabarlarni saqlash","Сохранить отредактированные сообщения"))
        titlesLanguages.add(TitleLanguages(31,"Save deleted messages","O'chirilgan xabarlarni saqlash","Сохранить удалённые сообщения"))
        titlesLanguages.add(TitleLanguages(32,"Ghost mode","Ghost rejimi","Режим призрака"))
        titlesLanguages.add(TitleLanguages(33,"Common menu","Umumiy menyusi","Общее меню"))
        titlesLanguages.add(TitleLanguages(34,"Select language","Tilni tanlang","Выберите язык"))
        titlesLanguages.add(TitleLanguages(35,"Password deleted!","Parol o'chirildi!","Пароль удален!"))
        titlesLanguages.add(TitleLanguages(36,"Password wrong!","Parol xato!","Неверный пароль!"))
        titlesLanguages.add(TitleLanguages(37,"Password changed!","Parol o'zgartirildi!","Пароль изменен!"))
        titlesLanguages.add(TitleLanguages(38,"Password created!","Parol yaratildi!","Пароль создан!"))
        titlesLanguages.add(TitleLanguages(39,"do You want to discard?","bekor qilmoqchimisiz?","Вы хотите отказаться?"))
        titlesLanguages.add(TitleLanguages(40,"do You want to save changes?","o'zgarishlarni saqlamoqchimisiz?","Вы хотите сохранить изменения?"))
        titlesLanguages.add(TitleLanguages(41,"copied","nusxalandi","скопировано"))
        titlesLanguages.add(TitleLanguages(42,"create a two-step password first","avval ikki bosqichli parol yarating","сначала создайте двухэтапный пароль"))
        titlesLanguages.add(TitleLanguages(43,"If you don't remember your password, enter your two-step password","Agar parolingizni eslamasangiz, ikki bosqichli parolni kiriting","Если вы не помните свой пароль, введите двухэтапный пароль"))
    }

    fun getMyTitles(code: Int):String{
        return when(languageCode){
            "en"->{
                titlesLanguages[code].en
            }
            "ru"->{
                titlesLanguages[code].ru
            }
            "uz"->{
                titlesLanguages[code].uz
            }
            else->{
                titlesLanguages[code].en
            }
        }
    }

    fun selectLanguage(parentActivity: Activity,menuOrChat:Boolean) {
        val type = sharedPreferences.getInt("type", -1)
        if (type != -1) {
            listen(type, parentActivity)
        } else {
            showLanguageDialog(parentActivity,menuOrChat)
        }
    }

    private fun saveLanguage(type: Int) {
        editor.putInt("type", type)
        editor.commit()
    }

    fun showLanguageDialog(parentActivity: Activity,menuOrChat:Boolean) {
        val languagesCodeName = languages
        val l = arrayOfNulls<CharSequence>(languagesCodeName.size)
        for (i in languagesCodeName.indices) {
            l[i] = languagesCodeName[i].name
        }

        val builder = AlertDialog.Builder(parentActivity)
        builder.setTitle(LanguageCode.getMyTitles(34))
        builder.setItems(l) { dialog: DialogInterface?, which: Int ->
            if (!menuOrChat){
                listen(which, parentActivity)
            }
            saveLanguage(which)
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
        builder.show()
    }

    private fun listen(type: Int, parentActivity: Activity) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        var languageCode = "en-EN"
        var tv = "Speak something..."
        when (type) {
            0 -> {
                languageCode = "en-EN"
                tv = getMyTitles(0)
            }

            1 -> {
                languageCode = "uz-UZ"
                tv = getMyTitles(0)
            }

            2 -> {
                languageCode = "ru-RU"
                tv = getMyTitles(0)
            }

            else -> {
                languageCode = languages[type].code
                tv = "Speak something..."
            }
        }
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, tv)

        try {
            ActivityCompat.startActivityForResult(parentActivity, intent, 1000, Bundle())
        } catch (e: Exception) {
            Toast.makeText(parentActivity, e.message, Toast.LENGTH_SHORT)
                .show()
        }
    }

}

class Language(val name: String, val code: String)

class TitleLanguages(val code:Int,val en:String,val uz:String,val ru:String)
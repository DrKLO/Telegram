package ua.itaysonlab.catogram.preferences

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow

class DonatePreferenceEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("CG_Donate", R.string.CG_Donate)) {
        category(LocaleController.getString("CG_Donate_Method", R.string.CG_Donate_Method)) {
            textIcon {
                title = "PayPal"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    val openURL = Intent(Intent.ACTION_VIEW)
                    openURL.data = Uri.parse("https://paypal.me/alivelegend")
                    bf.parentActivity.startActivity(openURL)
                }
            }
            textIcon {
                title = "QIWI"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    val openURL = Intent(Intent.ACTION_VIEW)
                    openURL.data = Uri.parse("https://qiwi.com/n/hackerone228/")
                    bf.parentActivity.startActivity(openURL)
                }
            }
            textIcon {
                title = "DonatePay"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    val openURL = Intent(android.content.Intent.ACTION_VIEW)
                    openURL.data = Uri.parse("https://new.donatepay.ru/@ctwoon")
                    bf.parentActivity.startActivity(openURL)
                }
            }
            textIcon {
                title = "YooMoney"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    val openURL = Intent(android.content.Intent.ACTION_VIEW)
                    openURL.data = Uri.parse("https://yoomoney.ru/to/4100116987294793")
                    bf.parentActivity.startActivity(openURL)
                }
            }
            textIcon {
                title = "Sberbank"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    AndroidUtilities.addToClipboard("4274320072473963")
                    Toast.makeText(bf.parentActivity, LocaleController.getString("CardNumberCopied", R.string.CardNumberCopied), Toast.LENGTH_SHORT).show()
                }
            }
            textIcon {
                title = "Monero"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    AndroidUtilities.addToClipboard("89ZEU9TRPDTcHAhFDTEQbsc6ZhA7fsdQKXewTgyo2kVPKGd2Uu3WfGwfW5T6x1kgPzW9njMXRL3gpU4qXjNkPXUVPV4p7cg")
                    Toast.makeText(bf.parentActivity, LocaleController.getString("CardNumberCopied", R.string.CardNumberCopied), Toast.LENGTH_SHORT).show()
                }
            }
            textIcon {
                title = "Ethereum"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    AndroidUtilities.addToClipboard("0x009edea9d0a196884619cadfd8f3a59fe9d2d12c")
                    Toast.makeText(bf.parentActivity, LocaleController.getString("CardNumberCopied", R.string.CardNumberCopied), Toast.LENGTH_SHORT).show()
                }
            }
            textIcon {
                title = "Bitcoin"
                divider = true

                listener = TGKitTextIconRow.TGTIListener {
                    AndroidUtilities.addToClipboard("3JQ7rBmnbJBxQQ25TsFLzy1F74k3jgfcYj")
                    Toast.makeText(bf.parentActivity, LocaleController.getString("CardNumberCopied", R.string.CardNumberCopied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
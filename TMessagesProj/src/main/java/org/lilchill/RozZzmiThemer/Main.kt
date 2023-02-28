package org.lilchill.RozZzmiThemer;

import android.content.Context
import android.util.Log
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ThemePreviewActivity
import java.io.File

class Main {
    private fun createTheme(file: String, themeProps: ThemeModel): String {
        val tints: Map<String, String> = ColorsUtils.getColorTints(themeProps)
        val hexesNames: Map<String, String?>

        val black = if(themeProps.isAmoled) "#000000" else "#181818"

        hexesNames = mapOf(
            "bl_900" to black,
            "wh_100" to "#FFFFFF",
            "gr_200" to "#F0F0F0",
            "gr_300" to "#DBDBDB",
            "gr_500" to "#919191",
            "gr_700" to "#707070",
            "gr_800" to "#464646",
            "gr_900" to "#202020",
            "ac_200" to tints["ac_200"],
            "ac_300" to tints["ac_300"],
            "ac_500" to tints["ac_500"],
            "ac_700" to tints["ac_700"],
            "ac_800" to tints["ac_800"],
            "yl_500" to "#E3B727",
            "yl_700" to "#DFA000",
            "rd_300" to "#FF6666",
            "rd_500" to "#DE4747",
            "rd_700" to "#C64949",
            "gn_300" to "#9ED448",
            "gn_500" to "#50B232",
            "gn_700" to "#26972C",
            "pu_500" to "#8E85EE",
            "tr_500" to "#00000000",
            "tr_ac300" to "#44${tints["ac_300"]?.drop(1)}",
            "tr_ac500" to "#77${tints["ac_500"]?.drop(1)}",
            "tr_gr300" to "#77DBDBDB",
            "tr_gr500" to "#77919191",
            "tr_gr700" to "#AA707070",
            "tr_gr800" to "#AA464646",
        )

        Log.d("LILCHILL", hexesNames["bl_900"]!!)

        var theme = file

        hexesNames.forEach{
            it.value?.let { it1 -> theme = theme.replace(it.key, it1) }
        }

        if(!themeProps.isGradient) {
            theme = theme.replace("chat_outBubbleGradient", "NoGradient")
        }

        return theme
    }
    private fun getTemplate(themeProps: ThemeModel) : MutableList<String>{
        val filesNames = mutableListOf("theday.attheme", "theday_template.attheme")
        if (themeProps.isDefault) {
            if (themeProps.isDark) {
                filesNames[0] = if (themeProps.isAmoled) "TheAmoled.attheme" else "TheNight.attheme"
                filesNames[1] = "theday_dark_template.attheme"
            } else {
                filesNames[0] = "TheDay.attheme"
                filesNames[1] = "theday_template.attheme"
            }
        } else {
            if (themeProps.isDark) {
                filesNames[0] = if (themeProps.isAmoled) "Soza Amoled.attheme" else "Soza Night.attheme"
                filesNames[1] = "thesoza_dark_template.attheme"
            } else {
                filesNames[0] = "Soza Day.attheme"
                filesNames[1] = "thesoza_template.attheme"
            }
        }
        return filesNames
    }

    fun getTheme(context: Context, themeProps: ThemeModel) : String{
        val filesNames = getTemplate(themeProps)
        Log.d("LILCHILL", "Template name is gotten")
        val templateFile = context.assets.open(filesNames[1]).bufferedReader().readText()
        Log.d("LILCHILL", "Template is gotten")
        val theme = createTheme(templateFile, themeProps)
        Log.d("LILCHILL", "Theme string is created")
        val themeFile = File(context.filesDir, filesNames[0])
        Log.d("LILCHILL", "Theme file is created")
        themeFile.writeText(theme)
        Log.d("LILCHILL", "Theme string is written to file")
        return themeFile.path
    }

}
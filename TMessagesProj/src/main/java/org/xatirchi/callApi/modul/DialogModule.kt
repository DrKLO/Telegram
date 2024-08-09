package org.xatirchi.callApi.modul

data class Future(
    val type: Int? = null,
    val future: IcTitleDescription?,
    val video: String?,
    val littleFutures: ArrayList<IcTitleDescription?>?
)

data class IcTitleDescription(
    val svgIcon: String?,
    val titleDescriptions: ArrayList<TitleDescription?>?
)

data class TitleDescription(val languageCode: String?, val title: String?, val description: String?)
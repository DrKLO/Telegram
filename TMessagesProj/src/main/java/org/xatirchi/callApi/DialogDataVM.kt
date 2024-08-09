package org.xatirchi.callApi

import android.util.Log
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.PremiumPreviewFragment.PremiumFeatureData
import org.xatirchi.api.ApiService
import org.xatirchi.api.RetrofitClient
import org.xatirchi.callApi.DialogData.dialogDataList
import org.xatirchi.callApi.DialogData.fakeData
import org.xatirchi.callApi.modul.Future
import org.xatirchi.callApi.modul.IcTitleDescription
import org.xatirchi.callApi.modul.TitleDescription
import org.xatirchi.utils.LanguageCode
import org.xatirchi.utils.SvgUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Singleton

class DialogDataVM {

    private val TAG = "DialogDataVM"

    private val apiService = RetrofitClient.instance.create(ApiService::class.java)

    init {
        dialogData()
//        fakeData()
    }

    private fun dialogData() {
        apiService.dialogData().enqueue(object : Callback<ArrayList<Future>> {
            override fun onResponse(
                call: Call<ArrayList<Future>>,
                response: Response<ArrayList<Future>>
            ) {
                if (response.isSuccessful) {
                    dialogDataList.clear()
                    dialogDataList.addAll(response.body() ?: ArrayList())
                }
                Log.d(TAG, "onResponse: " + response.body() + "\n" + response.message())
            }

            override fun onFailure(call: Call<ArrayList<Future>>, t: Throwable) {
                Log.d(TAG, "onFailure: " + t.message)
            }

        })
    }
}


@Singleton
object DialogData {
    var dialogDataList = ArrayList<Future>()

    private fun getDialogModule(type: Int): Future? {
        return dialogDataList.find { it.type == type }
    }

    fun getDialogLikeStoriesFutures(type: Int): Future? {
        val future = getDialogModule(type)
        if (future != null) {
            if (future.video == "none") {
                return future
            }
        }
        return null
    }

    fun getDialogLikeVideoFutures(type: Int): Future? {
        val future = getDialogModule(type)
        if (future != null) {
            if (future.video != "none") {
                return future
            }
        }
        return null
    }

    val svgString = "<?xml version=\"1.0\" standalone=\"no\"?>" +
            "<svg width=\"100\" height=\"100\" xmlns=\"http://www.w3.org/2000/svg\">" +
            "<circle cx=\"50\" cy=\"50\" r=\"40\" stroke=\"black\" stroke-width=\"3\" fill=\"red\" />" +
            "</svg>"

    fun getAllLikeVideoFutures(): ArrayList<PremiumFeatureData> {
        val videoFutures = ArrayList<PremiumFeatureData>()
        val videoDialog = dialogDataList.filter { it.video != "none" }
        for (future in videoDialog) {
            val titleAndDesc = getTitleAndDesc(future.future?.titleDescriptions)
            if (titleAndDesc != null) {
                videoFutures.add(
                    PremiumFeatureData(
                        future.type ?: 0,
                        0,
                        PremiumPreviewFragment.applyNewSpan(titleAndDesc.title),
                        titleAndDesc.description,
                        SvgUtils.getDrawableFromSvg(future.future?.svgIcon)
                    )
                )
            }
        }
//        for (i in 100 until dialogDataList.size + 100) {
//            val videoDialog = getDialogLikeVideoFutures(i)
//            if (videoDialog != null) {
//                val titleAndDesc =
//                    videoDialog.future?.titleDescriptions?.find { it?.languageCode == LanguageCode.languageCode }
//                videoFutures.add(
//                    PremiumFeatureData(
//                        i,
//                        0,
//                        PremiumPreviewFragment.applyNewSpan(titleAndDesc?.title),
//                        titleAndDesc?.description,
//                        SvgUtils.getDrawableFromSvg(videoDialog.future?.svgIcon)
//                    )
//                )
//            }
//        }
        return videoFutures
    }

    fun getAllLikeStoriesFutures(): ArrayList<PremiumFeatureData> {
        val storiesFutures = ArrayList<PremiumFeatureData>()
        val storiesDialog = dialogDataList.filter { it.video == "none" }
        for (future in storiesDialog) {
            val titleAndDesc = getTitleAndDesc(future.future?.titleDescriptions)
            if (titleAndDesc != null) {
                storiesFutures.add(
                    PremiumFeatureData(
                        future.type ?: 0,
                        0,
                        PremiumPreviewFragment.applyNewSpan(titleAndDesc.title),
                        titleAndDesc.description,
                        SvgUtils.getDrawableFromSvg(future.future?.svgIcon)
                    )
                )
            }
        }
//        for (i in 100 until dialogDataList.size + 100) {
//            val storiesDialog = getDialogLikeStoriesFutures(i)
//            if (storiesDialog != null) {
//                val titleAndDesc =
//                    storiesDialog.future?.titleDescriptions?.find { it?.languageCode == LanguageCode.languageCode }
//                storiesFutures.add(
//                    PremiumFeatureData(
//                        i,
//                        0,
//                        PremiumPreviewFragment.applyNewSpan(titleAndDesc?.title),
//                        titleAndDesc?.description,
//                        SvgUtils.getDrawableFromSvg(storiesDialog.future?.svgIcon)
//                    )
//                )
//            }
//        }
        return storiesFutures
    }

    fun getTitleAndDesc(titleDescriptions: ArrayList<TitleDescription?>?): TitleDescription? {
        return titleDescriptions?.find { it?.languageCode == LanguageCode.languageCode }
    }

    fun fakeData() {
        val future1 = Future(
            type = 100,
            future = IcTitleDescription(
                svgIcon = svgString,
                titleDescriptions = arrayListOf(
                    TitleDescription("en", "Title 1", "Description 1"),
                    TitleDescription("uz", "Test", "TestUchun")
                )
            ),
            video = "none",
            littleFutures = arrayListOf(
                IcTitleDescription(
                    svgIcon = svgString,
                    titleDescriptions = arrayListOf(
                        TitleDescription("en", "Title 1", "Description 1")
                    )
                ),
                IcTitleDescription(
                    svgIcon = svgString,
                    titleDescriptions = arrayListOf(
                        TitleDescription("uz", "Test", "TestUchun")
                    )
                )
            )
        )

        val future2 = Future(
            type = 101,
            future = IcTitleDescription(
                svgIcon = svgString,
                titleDescriptions = arrayListOf(
                    TitleDescription("en", "Title 1", "Description 1"),
                    TitleDescription("uz", "Test", "TestUchun")
                )
            ),
            video = "https://rr3---sn-01oxu-ci3l.googlevideo.com/videoplayback?expire=1721147293&ei=PUuWZvz8FfSv6dsP48a08AU&ip=46.4.48.22&id=o-AOFPl6ac6waAmcy46jCZPKTa21YfD0Bg0gpiRETqCN9-&itag=18&source=youtube&requiressl=yes&xpc=EgVo2aDSNQ%3D%3D&vprv=1&svpuc=1&mime=video%2Fmp4&rqh=1&gir=yes&clen=83315119&ratebypass=yes&dur=979.580&lmt=1712733597139676&c=ANDROID_TESTSUITE&txp=4538434&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cxpc%2Cvprv%2Csvpuc%2Cmime%2Crqh%2Cgir%2Cclen%2Cratebypass%2Cdur%2Clmt&sig=AJfQdSswRQIgXacy8ef3xlpNC3wmh0BAj-z5eE3ad_yAYddtptZiP2oCIQD2rBgJJG5Q0Ns1eQAqjxEkiQ4GdbZe5z74FNamxUISjA%3D%3D&title=%D0%A3%D0%B7%D0%B1%D0%B5%D0%BA%20%D0%BA%D0%B8%D0%B7%20%D0%90%D0%BC%D0%B5%D1%80%D0%B8%D0%BA%D0%B0%D0%B4%D0%B0%20Tesla%20%D1%85%D0%B0%D0%B9%D0%B4%D0%B0%D0%B4%D0%B8&redirect_counter=1&rm=sn-4g5erl7z&rrc=104&fexp=24350516&req_id=6724d6cf9dbba3ee&cms_redirect=yes&cmsv=e&ipbypass=yes&mh=hd&mip=84.54.70.48&mm=31&mn=sn-01oxu-ci3l&ms=au&mt=1721125280&mv=m&mvi=3&pl=24&lsparams=ipbypass,mh,mip,mm,mn,ms,mv,mvi,pl&lsig=AHlkHjAwRQIgSWIBim0KkGe7vA_k-ysfbSHL5sc-zHXlxTfVxsj5RMQCIQDGcJ_Oclp4EMN1wyBWqOOLB1q-iR9LoX0bCYbAD6cYig%3D%3D",
            littleFutures = null
        )

        dialogDataList.add(future1)
        dialogDataList.add(future2)
    }
}
package org.xatirchi.ui.xatirchi_premium

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.R
import org.telegram.messenger.databinding.XatirchiPremiumBinding
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.PremiumPreviewFragment
import org.xatirchi.ui.xatirchi_premium.adapters.XatirchiPremiumFuturesAdapter
import org.xatirchi.ui.xatirchi_premium.modules.XatirchiPremiumModule

class XatirchiPremium : BaseFragment() {

    private val TAG = "XatirchiPremium"

    lateinit var premiumFutures: ArrayList<XatirchiPremiumModule>
    lateinit var xatirchiPremiumFuturesAdapter: XatirchiPremiumFuturesAdapter
    lateinit var binding: XatirchiPremiumBinding
    lateinit var sharedPreferences: SharedPreferences

    override fun createView(context: Context?): View {
        binding = XatirchiPremiumBinding.inflate(LayoutInflater.from(context))
        actionBar.setAddToContainer(false)


        sharedPreferences = parentActivity.getSharedPreferences("db", MODE_PRIVATE)

        premiumFutures = ArrayList()

        xatirchiPremiumFuturesAdapter = XatirchiPremiumFuturesAdapter(premiumFutures,
            object : XatirchiPremiumFuturesAdapter.XatirchiPremiumOnClick {
                override fun futureOnClick(xatirchiPremiumModule: XatirchiPremiumModule) {
                    showDialog(
                        PremiumFeatureBottomSheet(
                            this@XatirchiPremium,
                            PremiumPreviewFragment.PREMIUM_FEATURE_LIMITS,
                            false
                        ).setForceAbout()
                    )
                }
            })

        val layoutManager =
            LinearLayoutManager(parentActivity, LinearLayoutManager.VERTICAL, false);

        binding.rv.layoutManager = layoutManager
        binding.rv.adapter = xatirchiPremiumFuturesAdapter

        binding.backBtn.setOnClickListener {
            finishFragment()
        }

        binding.premiumBtn.setOnClickListener {
//            // Misol matn va kalit so'zi
//            val plaintext = "Bu matn XOR bilan shifrlangan va deshifrlangan."
//
//            // Matnni shifrlash va deshifrlash
//            val yopish = AESUtil.yopish(plaintext)
//            Log.d(TAG, "Shifrlangan matn (Base64): $yopish")
//
//            val ochish = AESUtil.ochish(yopish)
//            Log.d(TAG, "Deshifrlangan matn: $ochish")

        }

        getData()


//        showDialog(
//            PremiumFeatureBottomSheet(
//                this,
//                getContext(),
//                currentAccount,
//                false,
//                0,
//                false,
//                null
//            )
//        )

        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    fun getData() {
        for (i in 0..100) {
            premiumFutures.add(
                XatirchiPremiumModule(
                    R.drawable.ic_launcher_dr,
                    "Test$i",
                    "$i This is description\nfor xatirchi premium test"
                )
            )
        }
        xatirchiPremiumFuturesAdapter.notifyDataSetChanged()
    }

//    PremiumPreviewFragment("settings")


//        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
//        actionBar.setBackgroundDrawable(null)
//        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
//        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false)
//        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false)
//        actionBar.castShadows = false
//        actionBar.setAddToContainer(false)
//        actionBar.occupyStatusBar = Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet()
//        actionBar.setTitle(LocaleController.getString("PeopleNearby", R.string.PeopleNearby))
//        actionBar.titleTextView.alpha = 0.0f
//        actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
//            override fun onItemClick(id: Int) {
//                if (id == -1) {
//                    finishFragment()
//                }
//            }
//        })

}
package org.xatirchi.ui.xatirchi_premium.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.databinding.XatirchiPremiumFetureItemBinding
import org.xatirchi.ui.xatirchi_premium.modules.XatirchiPremiumModule

class XatirchiPremiumFuturesAdapter(
    var future: ArrayList<XatirchiPremiumModule>,
    var xatirchiPremiumOnClick: XatirchiPremiumOnClick
) :
    RecyclerView.Adapter<XatirchiPremiumFuturesAdapter.VH>() {

    inner class VH(var xatirchiPremiumFetureItemBinding: XatirchiPremiumFetureItemBinding) :
        RecyclerView.ViewHolder(xatirchiPremiumFetureItemBinding.root) {
        fun onBind(xatirchiPremiumModule: XatirchiPremiumModule) {
            xatirchiPremiumFetureItemBinding.futureIc.setImageResource(
                xatirchiPremiumModule.src ?: 0
            )
            xatirchiPremiumFetureItemBinding.futureTitle.text = xatirchiPremiumModule.title
            xatirchiPremiumFetureItemBinding.futureDesc.text = xatirchiPremiumModule.desc
            xatirchiPremiumFetureItemBinding.root.setOnClickListener {
                xatirchiPremiumOnClick.futureOnClick(xatirchiPremiumModule)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            XatirchiPremiumFetureItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.onBind(future[position])
    }

    override fun getItemCount(): Int = future.size

    interface XatirchiPremiumOnClick {
        fun futureOnClick(xatirchiPremiumModule: XatirchiPremiumModule)
    }

}
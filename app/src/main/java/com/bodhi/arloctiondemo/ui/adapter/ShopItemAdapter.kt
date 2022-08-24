package com.bodhi.arloctiondemo.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.ItemShopViewHolderBinding
import com.bodhi.arloctiondemo.mapShopItemImageDrawable

class ShopItemAdapter(
    val list: List<ShopItems>,
    val onSelectDelegate: (item: List<ShopItems>) -> Unit
) :
    RecyclerView.Adapter<ShopItemAdapter.ShopItemsViewHolder>() {
    private val tempSelectedList: ArrayList<ShopItems> = arrayListOf()
    override fun getItemCount(): Int = list.size

    inner class ShopItemsViewHolder(val itemBinding: ItemShopViewHolderBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun bindItem(item: ShopItems) {
            mapShopItemImageDrawable(itemView.context, item.itemCode)?.let {
                itemBinding.itemImage.setImageDrawable(it)
            }
            itemBinding.itemName.text = item.itemName
            itemBinding.mallId.text = item.mallCode.toString()
            val itemSelected = if (tempSelectedList.isEmpty()) false
            else tempSelectedList.first().itemCode == item.itemCode

            if (itemSelected) {
                itemBinding.checkbox.visibility = View.VISIBLE
            } else {
                itemBinding.checkbox.visibility = View.GONE
            }
            itemBinding.thumbItem.setOnClickListener {
                tempSelectedList.clear()
                tempSelectedList.add(item)
                onSelectDelegate(tempSelectedList)
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopItemsViewHolder {
        return ShopItemsViewHolder(
            ItemShopViewHolderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ShopItemsViewHolder, position: Int) {
        holder.bindItem(list[position])
    }
}
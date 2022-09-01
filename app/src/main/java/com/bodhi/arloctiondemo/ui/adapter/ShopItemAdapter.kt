package com.bodhi.arloctiondemo.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.ItemShopViewHolderBinding
import com.bodhi.arloctiondemo.mapShopItemImageDrawable

class ShopItemAdapter(
    val onSelectDelegate: (item: List<ShopItems>) -> Unit
) : RecyclerView.Adapter<ShopItemAdapter.ShopItemsViewHolder>() {
    private var shopList: List<ShopItems> = emptyList()

    fun updateShopList(list: List<ShopItems>) {
        shopList = list
        notifyDataSetChanged()
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
        holder.bindItem(shopList[position])
    }

    override fun getItemCount(): Int = shopList.size

    fun filterList(filteredList: ArrayList<ShopItems>) {
        shopList = filteredList
        notifyDataSetChanged()
    }


    inner class ShopItemsViewHolder(val itemBinding: ItemShopViewHolderBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun bindItem(item: ShopItems) {
            mapShopItemImageDrawable(itemView.context, item.itemCode)?.let {
                itemBinding.itemImage.setImageDrawable(it)
            }
            itemBinding.itemName.text = item.itemName
            itemBinding.itemDescription.text =
                "Item Code: " + item.itemCode + "\n" + "Mall ID: " + item.mallCode

            itemBinding.checkbox.visibility = item.isSelected.let {
                if (it) View.VISIBLE else View.GONE
            }

            itemBinding.thumbItem.setOnClickListener {
                item.isSelected = !item.isSelected
                itemBinding.checkbox.visibility = item.isSelected.let {
                    if (it) View.VISIBLE else View.GONE
                }
                onSelectDelegate(getSelectedShopItemList())
                notifyDataSetChanged()
            }
        }
    }

    fun getSelectedShopItemList(): ArrayList<ShopItems> {
        val selectedList: ArrayList<ShopItems> = arrayListOf()
        for (shopItem in shopList) {
            if (shopItem.isSelected) {
                selectedList.add(shopItem)
            }
        }
        return selectedList
    }


}
package com.bodhi.arloctiondemo.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bodhi.arloctiondemo.data.ShopItems
import com.bodhi.arloctiondemo.databinding.ItemMultiItemSelectionBinding
import com.bodhi.arloctiondemo.mapShopItemImageDrawable

class MultiItemSelectionAdapter(
    private val selectedShopItems: List<ShopItems>
) :
    RecyclerView.Adapter<MultiItemSelectionAdapter.MultiItemSelectionViewHolder>() {

    class MultiItemSelectionViewHolder(
        private val binding: ItemMultiItemSelectionBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ShopItems) {
            binding.headingText.text = item.itemName
            mapShopItemImageDrawable(itemView.context, item.itemCode)?.let {
                binding.itemImage.setImageDrawable(it)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MultiItemSelectionViewHolder {
        return MultiItemSelectionViewHolder(
            ItemMultiItemSelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MultiItemSelectionViewHolder, position: Int) {
        holder.bind(selectedShopItems[position])
    }

    override fun getItemCount(): Int = selectedShopItems.size
}
package com.yangtianyu.frameworklab.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yangtianyu.frameworklab.databinding.ItemExperimentBinding

/**
 * 将实验目录绑定到首页 RecyclerView，并把点击事件交给 Fragment 处理。
 */
class ExperimentAdapter(
    private val onExperimentClick: (ExperimentItem) -> Unit,
) : ListAdapter<ExperimentItem, ExperimentAdapter.ExperimentViewHolder>(ExperimentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperimentViewHolder {
        val binding = ItemExperimentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ExperimentViewHolder(binding, onExperimentClick)
    }

    override fun onBindViewHolder(holder: ExperimentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExperimentViewHolder(
        private val binding: ItemExperimentBinding,
        private val onExperimentClick: (ExperimentItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExperimentItem) {
            binding.titleText.text = item.title
            binding.descriptionText.text = item.description
            binding.root.setOnClickListener { onExperimentClick(item) }
        }
    }

    private object ExperimentDiffCallback : DiffUtil.ItemCallback<ExperimentItem>() {
        override fun areItemsTheSame(
            oldItem: ExperimentItem,
            newItem: ExperimentItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ExperimentItem,
            newItem: ExperimentItem,
        ): Boolean = oldItem == newItem
    }
}

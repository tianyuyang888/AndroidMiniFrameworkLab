package com.yangtianyu.frameworklab.imageloader

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yangtianyu.frameworklab.R
import com.yangtianyu.frameworklab.databinding.ItemImageDemoBinding

/**
 * 把演示 URL 绑定到 RecyclerView，并为每项调用 mini-image-loader 的公开 API。
 */
class ImageDemoAdapter :
    ListAdapter<String, ImageDemoAdapter.ImageDemoViewHolder>(UrlDiffCallback) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ImageDemoViewHolder {
        val binding = ItemImageDemoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ImageDemoViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ImageDemoViewHolder,
        position: Int,
    ) {
        holder.bind(
            url = getItem(position),
            displayPosition = position + 1,
        )
    }

    class ImageDemoViewHolder(
        private val binding: ItemImageDemoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            url: String,
            displayPosition: Int,
        ) {
            binding.urlText.text = url
            binding.demoImage.contentDescription = binding.root.context.getString(
                R.string.demo_image_content_description,
                displayPosition,
            )

            // 每次绑定都发起请求；Library 内的 keyed tag 会阻止复用后的旧请求回写。
            MiniImageLoader.load(
                url = url,
                imageView = binding.demoImage,
                placeholderResId = R.drawable.image_placeholder,
                errorResId = R.drawable.image_error,
            )
        }
    }

    private object UrlDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}

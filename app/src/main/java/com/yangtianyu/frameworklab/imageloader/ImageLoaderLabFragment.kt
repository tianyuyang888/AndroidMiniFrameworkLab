package com.yangtianyu.frameworklab.imageloader

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yangtianyu.frameworklab.R
import com.yangtianyu.frameworklab.databinding.FragmentImageLoaderLabBinding

/**
 * 第一版图片加载器演示页，使用 RecyclerView 同时触发多张网络图片加载。
 */
class ImageLoaderLabFragment : Fragment(R.layout.fragment_image_loader_lab) {

    private var _binding: FragmentImageLoaderLabBinding? = null
    private val binding: FragmentImageLoaderLabBinding
        get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentImageLoaderLabBinding.bind(view)
        binding.moduleNameText.text = MiniImageLoader.NAME

        val imageAdapter = ImageDemoAdapter()
        binding.imageList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = imageAdapter
            setHasFixedSize(true)
        }
        imageAdapter.submitList(ImageDemoCatalog.urls())
    }

    override fun onDestroyView() {
        binding.imageList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

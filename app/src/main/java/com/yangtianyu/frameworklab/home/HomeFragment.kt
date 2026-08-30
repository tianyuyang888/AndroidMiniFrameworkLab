package com.yangtianyu.frameworklab.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.yangtianyu.frameworklab.R
import com.yangtianyu.frameworklab.databinding.FragmentHomeBinding

/**
 * 应用首页，使用 RecyclerView 展示当前可进入的实验模块。
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = checkNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        val experimentAdapter = ExperimentAdapter(::openExperiment)
        binding.experimentList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = experimentAdapter
            setHasFixedSize(true)
        }
        experimentAdapter.submitList(ExperimentCatalog.all())
    }

    private fun openExperiment(item: ExperimentItem) {
        val navController = findNavController()
        val destination = ExperimentNavigationPolicy.destinationFor(
            item = item,
            isHomeCurrentDestination = navController.currentDestination?.id == R.id.homeFragment,
        )
        when (destination) {
            ExperimentDestination.IMAGE_LOADER ->
                navController.navigate(R.id.action_homeFragment_to_imageLoaderLabFragment)
            ExperimentDestination.VEHICLE_STATUS ->
                navController.navigate(R.id.action_homeFragment_to_vehicleDashboardFragment)
            null -> Unit
        }
    }

    override fun onDestroyView() {
        binding.experimentList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

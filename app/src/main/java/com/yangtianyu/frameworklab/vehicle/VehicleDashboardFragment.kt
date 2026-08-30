package com.yangtianyu.frameworklab.vehicle

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.yangtianyu.frameworklab.R
import com.yangtianyu.frameworklab.databinding.FragmentVehicleDashboardBinding

/**
 * 车辆状态页面只负责把 ViewModel 状态渲染到 XML View，业务判断集中在 reducer 和服务端。
 */
class VehicleDashboardFragment : Fragment(R.layout.fragment_vehicle_dashboard) {
    private var _binding: FragmentVehicleDashboardBinding? = null
    private val binding: FragmentVehicleDashboardBinding
        get() = checkNotNull(_binding)

    private val viewModel: VehicleDashboardViewModel by viewModels {
        VehicleDashboardViewModel.Factory(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVehicleDashboardBinding.bind(view)
        binding.temperatureDecreaseButton.setOnClickListener { viewModel.decreaseTemperature() }
        binding.temperatureIncreaseButton.setOnClickListener { viewModel.increaseTemperature() }
        binding.acButton.setOnClickListener { viewModel.toggleAc() }
        binding.unlockDoorsButton.setOnClickListener { viewModel.unlockAllDoors() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.simulateDisconnectButton.setOnClickListener { viewModel.simulateProcessDeath() }
        binding.simulateParkedButton.setOnClickListener {
            viewModel.applySimulationPreset(VehicleSimulationPreset.PARKED)
        }
        binding.simulateDrivingButton.setOnClickListener {
            viewModel.applySimulationPreset(VehicleSimulationPreset.DRIVING)
        }
        binding.simulateRearRightDoorButton.setOnClickListener {
            viewModel.applySimulationPreset(VehicleSimulationPreset.REAR_RIGHT_DOOR_OPEN)
        }
        val isDebuggable = requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        binding.debugConsole.visibility = if (isDebuggable) View.VISIBLE else View.GONE
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    private fun render(state: VehicleDashboardUiState) {
        binding.connectionStatusText.text = getString(
            when (state.connectionStatus) {
                VehicleConnectionStatus.DISCONNECTED -> R.string.vehicle_connection_disconnected
                VehicleConnectionStatus.CONNECTING -> R.string.vehicle_connection_connecting
                VehicleConnectionStatus.CONNECTED -> R.string.vehicle_connection_connected
                VehicleConnectionStatus.RETRY_WAITING -> R.string.vehicle_connection_retry_waiting
            },
        )
        binding.staleBanner.visibility = if (state.isDataStale) View.VISIBLE else View.GONE
        binding.retryButton.visibility =
            if (state.connectionStatus == VehicleConnectionStatus.DISCONNECTED) View.VISIBLE else View.GONE

        val hasFreshSnapshot = state.snapshot != null && !state.isDataStale
        binding.temperatureDecreaseButton.isEnabled = hasFreshSnapshot
        binding.temperatureIncreaseButton.isEnabled = hasFreshSnapshot
        binding.acButton.isEnabled = hasFreshSnapshot
        binding.unlockDoorsButton.isEnabled = !state.isDataStale && state.canUnlockAllDoors
        binding.simulateDisconnectButton.isEnabled = state.connectionStatus == VehicleConnectionStatus.CONNECTED
        val canUsePresets = state.connectionStatus == VehicleConnectionStatus.CONNECTED &&
            !state.isDataStale &&
            state.canEditSimulation
        binding.simulateParkedButton.isEnabled = canUsePresets
        binding.simulateDrivingButton.isEnabled = canUsePresets
        binding.simulateRearRightDoorButton.isEnabled = canUsePresets
        binding.restrictionText.visibility =
            if (!state.isDataStale && state.canUnlockAllDoors) View.GONE else View.VISIBLE
        renderCommandResult(state.commandResult)

        val snapshot = state.snapshot
        if (snapshot == null) {
            // 首帧尚未到达时主动清空所有车辆值，避免展示上一次 View 的误导性残留。
            val unavailable = getString(R.string.vehicle_value_unavailable)
            binding.speedText.text = unavailable
            binding.gearText.text = unavailable
            binding.batteryText.text = unavailable
            binding.rangeText.text = unavailable
            binding.doorStatusText.text = unavailable
            binding.temperatureText.text = unavailable
            binding.fanText.text = unavailable
            binding.acButton.text = unavailable
            return
        }

        binding.speedText.text = getString(R.string.vehicle_speed_value, snapshot.speedKph)
        binding.gearText.text = getString(
            when (snapshot.gear) {
                VehicleGear.PARK -> R.string.vehicle_gear_park
                VehicleGear.REVERSE -> R.string.vehicle_gear_reverse
                VehicleGear.NEUTRAL -> R.string.vehicle_gear_neutral
                VehicleGear.DRIVE -> R.string.vehicle_gear_drive
            },
        )
        binding.batteryText.text = getString(R.string.vehicle_battery_value, snapshot.batteryPercent)
        binding.rangeText.text = getString(R.string.vehicle_range_value, snapshot.rangeKm)
        binding.temperatureText.text = getString(R.string.vehicle_temperature_value, snapshot.temperatureCelsius)
        binding.fanText.text = getString(R.string.vehicle_fan_value, snapshot.fanSpeed)
        binding.acButton.text = getString(if (snapshot.isAcOn) R.string.vehicle_ac_on else R.string.vehicle_ac_off)
        val doors = VehicleDashboardDisplayMapper.doorDisplay(snapshot)
        binding.doorStatusText.text = getString(
            R.string.vehicle_door_status_format,
            doorStateText(doors.isFrontLeftOpen),
            doorStateText(doors.isFrontRightOpen),
            doorStateText(doors.isRearLeftOpen),
            doorStateText(doors.isRearRightOpen),
            getString(if (doors.areDoorsLocked) R.string.vehicle_locked else R.string.vehicle_unlocked),
        )
    }

    private fun doorStateText(isOpen: Boolean): String =
        getString(if (isOpen) R.string.vehicle_door_open else R.string.vehicle_door_closed)

    private fun renderCommandResult(result: Int?) {
        val message = VehicleDashboardDisplayMapper.commandResult(result)
        binding.commandResultText.visibility = if (message == null) View.GONE else View.VISIBLE
        binding.commandResultText.text = message?.let {
            getString(
                when (it) {
                    CommandResultMessage.SUCCESS -> R.string.vehicle_command_success
                    CommandResultMessage.INVALID_ARGUMENT -> R.string.vehicle_command_invalid_argument
                    CommandResultMessage.REJECTED_WHILE_DRIVING -> R.string.vehicle_command_rejected_driving
                    CommandResultMessage.SERVICE_UNAVAILABLE -> R.string.vehicle_command_service_unavailable
                    CommandResultMessage.DEBUG_ONLY -> R.string.vehicle_command_debug_only
                    CommandResultMessage.UNKNOWN -> R.string.vehicle_command_unknown
                },
            )
        }.orEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

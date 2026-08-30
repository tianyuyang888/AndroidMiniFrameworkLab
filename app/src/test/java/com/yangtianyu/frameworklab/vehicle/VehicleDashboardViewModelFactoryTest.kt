package com.yangtianyu.frameworklab.vehicle

import androidx.lifecycle.ViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDashboardViewModelFactoryTest {
    @Test
    fun dashboardModelClassIsAccepted() {
        requireDashboardModelClass(VehicleDashboardViewModel::class.java)
    }

    @Test
    fun unrelatedModelClassIsRejectedWithClearMessage() {
        val error = runCatching {
            requireDashboardModelClass(UnrelatedViewModel::class.java)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("VehicleDashboardViewModel") == true)
    }

    private class UnrelatedViewModel : ViewModel()
}

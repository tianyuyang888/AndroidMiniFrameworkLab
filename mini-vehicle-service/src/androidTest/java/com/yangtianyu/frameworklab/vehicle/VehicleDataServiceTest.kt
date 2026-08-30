package com.yangtianyu.frameworklab.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDataServiceTest {
    @Test
    fun bindsAndReceivesFirstSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connected = CountDownLatch(1)
        val snapshotReceived = CountDownLatch(1)
        var service: IVehicleService? = null
        val callback = object : IVehicleStateCallback.Stub() {
            override fun onVehicleSnapshotChanged(snapshot: VehicleSnapshot) {
                snapshotReceived.countDown()
            }
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = IVehicleService.Stub.asInterface(binder)
                service?.registerCallback(callback)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        val bound = context.bindService(
            Intent(context, VehicleDataService::class.java).putExtra(
                VehicleDataService.EXTRA_PAUSE_AUTO_SIMULATION,
                true,
            ),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        try {
            assertTrue(bound)
            assertTrue(connected.await(3, TimeUnit.SECONDS))
            assertNotNull(service?.currentSnapshot)
            assertTrue(snapshotReceived.await(3, TimeUnit.SECONDS))

            val pausedSnapshot = requireNotNull(service?.currentSnapshot)
            Thread.sleep(1_200)
            assertEquals(pausedSnapshot, service?.currentSnapshot)

            val parked = requireNotNull(service?.currentSnapshot).copy(
                speedKph = 0,
                gear = VehicleGear.PARK,
                areDoorsLocked = true,
            )
            assertEquals(
                VehicleCommandResult.SUCCESS,
                service?.setSimulationSnapshot(parked),
            )
            assertEquals(VehicleCommandResult.SUCCESS, service?.unlockAllDoors())
            assertFalse(requireNotNull(service?.currentSnapshot).areDoorsLocked)

            val moving = requireNotNull(service?.currentSnapshot).copy(
                speedKph = 1,
                gear = VehicleGear.DRIVE,
            )
            assertEquals(
                VehicleCommandResult.SUCCESS,
                service?.setSimulationSnapshot(moving),
            )
            assertEquals(
                VehicleCommandResult.REJECTED_WHILE_DRIVING,
                service?.unlockAllDoors(),
            )
        } finally {
            service?.unregisterCallback(callback)
            if (bound) context.unbindService(connection)
        }
    }
}

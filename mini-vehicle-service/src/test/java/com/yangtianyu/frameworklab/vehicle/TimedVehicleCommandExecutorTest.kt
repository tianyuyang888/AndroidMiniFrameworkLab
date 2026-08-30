package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimedVehicleCommandExecutorTest {
    @Test
    fun timedOutQueuedCommandIsCancelledBeforeItCanChangeState() {
        val executor = Executors.newSingleThreadExecutor()
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val state = AtomicInteger(0)

        try {
            executor.execute {
                blockerStarted.countDown()
                releaseBlocker.await()
            }
            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS))
            val commandExecutor = TimedVehicleCommandExecutor(
                executor = executor,
                timeout = 50,
                timeoutUnit = TimeUnit.MILLISECONDS,
            )

            val result = commandExecutor.execute {
                state.incrementAndGet()
                VehicleCommandResult.SUCCESS
            }

            assertEquals(VehicleCommandResult.SERVICE_UNAVAILABLE, result)
            releaseBlocker.countDown()
            executor.submit {}.get(1, TimeUnit.SECONDS)
            assertEquals(0, state.get())
        } finally {
            releaseBlocker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun interruptedWaitCancelsQueuedCommandAndRestoresInterrupt() {
        val executor = ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
        )
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        val commandExecuted = AtomicBoolean(false)
        val interruptRestored = AtomicBoolean(false)
        val result = AtomicInteger(Int.MIN_VALUE)

        try {
            executor.execute {
                blockerStarted.countDown()
                releaseBlocker.await()
            }
            assertTrue(blockerStarted.await(1, TimeUnit.SECONDS))
            val commandExecutor = TimedVehicleCommandExecutor(
                executor = executor,
                timeout = 5,
                timeoutUnit = TimeUnit.SECONDS,
            )
            val caller = Thread {
                result.set(
                    commandExecutor.execute {
                        commandExecuted.set(true)
                        VehicleCommandResult.SUCCESS
                    },
                )
                interruptRestored.set(Thread.currentThread().isInterrupted)
            }

            caller.start()
            val queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (executor.queue.isEmpty() && System.nanoTime() < queueDeadline) {
                Thread.yield()
            }
            assertFalse(executor.queue.isEmpty())
            caller.interrupt()
            caller.join(TimeUnit.SECONDS.toMillis(1))

            assertFalse(caller.isAlive)
            assertEquals(VehicleCommandResult.SERVICE_UNAVAILABLE, result.get())
            assertTrue(interruptRestored.get())
            releaseBlocker.countDown()
            executor.submit {}.get(1, TimeUnit.SECONDS)
            assertFalse(commandExecuted.get())
        } finally {
            releaseBlocker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun failedCommandReturnsServiceUnavailable() {
        val executor = Executors.newSingleThreadExecutor()
        val commandExecutor = TimedVehicleCommandExecutor(
            executor = executor,
            timeout = 1,
            timeoutUnit = TimeUnit.SECONDS,
        )

        try {
            assertEquals(
                VehicleCommandResult.SERVICE_UNAVAILABLE,
                commandExecutor.execute { error("command failed") },
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectedCommandReturnsServiceUnavailable() {
        val executor = Executors.newSingleThreadExecutor()
        val commandExecutor = TimedVehicleCommandExecutor(
            executor = executor,
            timeout = 1,
            timeoutUnit = TimeUnit.SECONDS,
        )
        executor.shutdownNow()

        assertEquals(
            VehicleCommandResult.SERVICE_UNAVAILABLE,
            commandExecutor.execute { VehicleCommandResult.SUCCESS },
        )
    }
}

package com.yangtianyu.frameworklab.vehicle

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderedCallbackDispatcherTest {
    @Test
    fun callbackRegisteredAfterQueuedUpdatesReceivesOnlyRegistrationSnapshot() {
        val executor = QueuedExecutor()
        val listeners = linkedSetOf<(String) -> Unit>()
        val dispatcher = OrderedCallbackDispatcher<(String) -> Unit, String>(
            executor = executor,
            registerCallback = listeners::add,
            unregisterCallback = listeners::remove,
            deliverInitial = { listener, value -> listener(value) },
            broadcastSnapshot = { value -> listeners.forEach { it(value) } },
        )
        val received = mutableListOf<String>()
        val listener: (String) -> Unit = received::add

        dispatcher.broadcast("A")
        dispatcher.broadcast("B")
        dispatcher.registerWithInitial(listener, "B")
        executor.runAll()

        assertEquals(listOf("B"), received)
    }

    @Test
    fun broadcastsAreDeliveredInFifoOrder() {
        val executor = QueuedExecutor()
        val listeners = linkedSetOf<(String) -> Unit>()
        val dispatcher = OrderedCallbackDispatcher<(String) -> Unit, String>(
            executor = executor,
            registerCallback = listeners::add,
            unregisterCallback = listeners::remove,
            deliverInitial = { listener, value -> listener(value) },
            broadcastSnapshot = { value -> listeners.forEach { it(value) } },
        )
        val received = mutableListOf<String>()

        val listener: (String) -> Unit = received::add
        dispatcher.registerWithInitial(listener, "initial")
        dispatcher.broadcast("A")
        dispatcher.broadcast("B")
        executor.runAll()

        assertEquals(listOf("initial", "A", "B"), received)
    }

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}

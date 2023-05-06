package com.badoo.reaktive.looperthread

import com.badoo.reaktive.utils.atomic.AtomicBoolean
import com.badoo.reaktive.utils.lock.ConditionLock
import com.badoo.reaktive.utils.lock.synchronized
import com.badoo.reaktive.utils.lock.waitForOrFail
import platform.posix.usleep
import kotlin.system.getTimeMillis
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class LooperThreadTest {

    @Test
    fun executes_task() {
        val isExecuted = AtomicBoolean()
        val lock = ConditionLock()
        val thread = LooperThread()
        val startTime = getTimeMillis() + 200L

        thread.schedule(Unit, startTime.milliseconds) {
            lock.synchronized {
                isExecuted.value = true
                lock.signal()
            }
        }

        lock.synchronized {
            lock.waitForOrFail(predicate = isExecuted::value)
        }

        assertTrue(getTimeMillis() >= startTime)
    }

    @Test
    fun does_not_execute_task_after_destroy() {
        val isExecuted = AtomicBoolean()
        val lock = ConditionLock()
        val thread = LooperThread()

        thread.schedule(Unit, getTimeMillis().milliseconds) {
            lock.synchronized {
                isExecuted.value = true
                lock.signal()
            }
        }

        lock.synchronized {
            lock.waitForOrFail(predicate = isExecuted::value)
        }

        thread.destroy()

        isExecuted.value = false
        thread.schedule(Unit, getTimeMillis().milliseconds) {
            isExecuted.value = true
        }

        usleep(200_000)

        assertFalse(isExecuted.value)
    }
}

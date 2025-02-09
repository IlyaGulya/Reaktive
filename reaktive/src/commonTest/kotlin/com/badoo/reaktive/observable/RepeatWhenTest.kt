package com.badoo.reaktive.observable

import com.badoo.reaktive.disposable.Disposable
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.maybeUnsafe
import com.badoo.reaktive.test.base.assertError
import com.badoo.reaktive.test.maybe.TestMaybe
import com.badoo.reaktive.test.observable.TestObservable
import com.badoo.reaktive.test.observable.assertComplete
import com.badoo.reaktive.test.observable.assertValues
import com.badoo.reaktive.test.observable.onNext
import com.badoo.reaktive.test.observable.test
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RepeatWhenTest : ObservableToObservableTests by ObservableToObservableTestsImpl({ repeatWhen { maybeOfEmpty<Unit>() } }) {

    @Test
    fun emits_all_values_from_all_iterations_in_order_WHEN_upstream_and_handler_are_asynchronous() {
        val upstream = TestObservable<Int?>()
        val handlerMaybes = List(3) { TestMaybe<Unit>() }

        val observer = upstream.repeatWhen { handlerMaybes[it] }.test()
        upstream.onNext(0, null)
        upstream.onComplete()
        handlerMaybes[1].onSuccess(Unit)
        upstream.onNext(1, null)
        upstream.onComplete()
        handlerMaybes[2].onSuccess(Unit)
        upstream.onNext(2, null)

        observer.assertValues(0, null, 1, null, 2, null)
    }

    @Test
    fun emits_all_values_from_all_observers_in_order_WHEN_upstream_and_handler_are_synchronous() {
        var number = 0
        val upstream =
            observableUnsafe<Int?> { observer ->
                observer.onSubscribe(Disposable())
                observer.onNext(++number)
                observer.onNext(null)
                observer.onComplete()
            }

        val observer =
            upstream
                .repeatWhen { repeatNumber ->
                    maybeUnsafe { observer ->
                        observer.onSubscribe(Disposable())
                        if (repeatNumber < 3) {
                            observer.onSuccess(Unit)
                        } else {
                            observer.onComplete()
                        }
                    }
                }
                .test()

        observer.assertValues(1, null, 2, null, 3, null)
    }

    @Test
    fun does_not_subscribe_to_upstream_recursively() {
        var subscribeCounter = 0
        var maxSubscribers = 0
        val upstream =
            observableUnsafe<Int?> { observer ->
                subscribeCounter++
                maxSubscribers = max(maxSubscribers, subscribeCounter)
                observer.onSubscribe(Disposable())
                observer.onComplete()
                subscribeCounter--
            }

        upstream
            .repeatWhen { repeatNumber ->
                maybeUnsafe { observer ->
                    observer.onSubscribe(Disposable())
                    if (repeatNumber == 1) {
                        observer.onSuccess(Unit)
                    } else {
                        observer.onComplete()
                    }
                }
            }
            .test()

        assertEquals(1, maxSubscribers)
    }

    @Test
    fun completes_WHEN_handler_completed_after_first_iteration() {
        val upstream = TestObservable<Int?>()
        val handlerMaybe = TestMaybe<Unit>()

        val observer = upstream.repeatWhen { handlerMaybe }.test()
        upstream.onComplete()
        handlerMaybe.onComplete()

        observer.assertComplete()
    }

    @Test
    fun completes_WHEN_handler_completed_after_second_iteration() {
        val upstream = TestObservable<Int?>()
        val handlerMaybe = TestMaybe<Unit>()

        val observer = upstream.repeatWhen { handlerMaybe }.test()
        upstream.onComplete()
        handlerMaybe.onSuccess(Unit)
        upstream.onComplete()
        handlerMaybe.onComplete()

        observer.assertComplete()
    }

    @Test
    fun produces_error_WHEN_handler_produced_error_after_first_iteration() {
        val upstream = TestObservable<Int?>()
        val handlerMaybe = TestMaybe<Unit>()
        val error = Exception()

        val observer = upstream.repeatWhen { handlerMaybe }.test()
        upstream.onComplete()
        handlerMaybe.onError(error)

        observer.assertError(error)
    }

    @Test
    fun produces_error_WHEN_handler_produced_error_after_second_iteration() {
        val upstream = TestObservable<Int?>()
        val handlerMaybe = TestMaybe<Unit>()
        val error = Exception()

        val observer = upstream.repeatWhen { handlerMaybe }.test()
        upstream.onComplete()
        handlerMaybe.onSuccess(Unit)
        upstream.onComplete()
        handlerMaybe.onError(error)

        observer.assertError(error)
    }

    @Test
    fun produces_error_WHEN_second_iteration_upstream_produced_error() {
        val upstream = TestObservable<Int?>()
        val error = Exception()

        val observer = upstream.repeatWhen { maybeOf(Unit) }.test()
        upstream.onComplete()
        upstream.onError(error)

        observer.assertError(error)
    }

    @Test
    fun predicate_receives_valid_attempt_WHEN_upstream_completes() {
        val upstream = TestObservable<Int?>()
        var timeRef = 0
        upstream
            .repeatWhen { attempt ->
                timeRef = attempt
                maybeOf(Unit)
            }
            .test()

        upstream.onComplete()
        assertSame(timeRef, 1)
        upstream.onComplete()
        assertSame(timeRef, 2)
    }
}

package com.software.sello.testing

import com.software.sello.domain.port.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Every dispatcher is one standard test dispatcher on [scheduler], so virtual time rules them all. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(scheduler: TestCoroutineScheduler) : DispatcherProvider {
    private val dispatcher = StandardTestDispatcher(scheduler)
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
}

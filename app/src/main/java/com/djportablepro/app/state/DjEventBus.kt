package com.djportablepro.app.state

import com.djportablepro.app.midi.mapping.DjAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.CopyOnWriteArrayList


class DjEventBus {
    private val listeners = CopyOnWriteArrayList<(DjAction) -> Unit>()
    private val _actions = MutableSharedFlow<DjAction>(extraBufferCapacity = 64)
    val actions = _actions.asSharedFlow()

    fun addListener(listener: (DjAction) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (DjAction) -> Unit) {
        listeners.remove(listener)
    }

    suspend fun emit(action: DjAction) {
        _actions.emit(action)
        listeners.forEach { it(action) }
    }

    fun tryEmit(action: DjAction) {
        _actions.tryEmit(action)
        listeners.forEach { it(action) }
    }
}

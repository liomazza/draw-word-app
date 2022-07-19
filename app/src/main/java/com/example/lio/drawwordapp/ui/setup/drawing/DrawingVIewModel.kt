package com.example.lio.drawwordapp.ui.setup.drawing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lio.drawwordapp.R
import com.example.lio.drawwordapp.data.remote.ws.DrawingApi
import com.example.lio.drawwordapp.data.remote.ws.models.*
import com.example.lio.drawwordapp.data.remote.ws.models.DrawAction.Companion.ACTION_UNDO
import com.example.lio.drawwordapp.util.DispatcherProvider
import com.google.gson.Gson
import com.tinder.scarlet.WebSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawingVIewModel @Inject constructor(
    private val drawingApi: DrawingApi,
    private val dispatchers: DispatcherProvider,
    private val gson: Gson
): ViewModel() {

    sealed class SocketEvent {
        data class ChatMessageEvent(val data: ChatMessage): SocketEvent()
        data class AnnouncementEvent(val data: Announcement): SocketEvent()
        data class GameStateEvent(val data: GameState): SocketEvent()
        data class DrawDataEvent(val data: DrawData): SocketEvent()
        data class NewWordsEvent(val data: NewWords): SocketEvent()
        data class GameErrorEvent(val data: GameError): SocketEvent()
        data class RoundDrawInfoEvent(val data: RoundDrawInfo): SocketEvent()
        object UndoEvent: SocketEvent()
    }

    private val _selectColorButtonId = MutableStateFlow(R.id.rbBlack)
    val selectedColorButtonId: StateFlow<Int> = _selectColorButtonId

    //Channel where we sent connections events messages
    private val connectionEventChannel = Channel<WebSocket.Event>()
    val connectionEvent = connectionEventChannel.receiveAsFlow().flowOn(dispatchers.io)

    private val socketsEventChannel = Channel<SocketEvent>()
    val socketEvent = connectionEventChannel.receiveAsFlow().flowOn(dispatchers.io)

    fun checkRadioButton(id: Int) {
        _selectColorButtonId.value = id
    }

    fun observeEvents() {
        viewModelScope.launch(dispatchers.io) {
            drawingApi.observeEvents().collect { event ->
                connectionEventChannel.send(event)
            }
        }
    }

    fun observeBaseModels() {
        viewModelScope.launch(dispatchers.io) {
            drawingApi.observeBaseModels().collect { data ->
                when(data) {
                    is DrawData -> {
                        socketsEventChannel.send(SocketEvent.DrawDataEvent(data))
                    }
                    is DrawAction -> {
                        when(data.action) {
                            ACTION_UNDO -> socketsEventChannel.send((SocketEvent.UndoEvent))
                        }
                    }
                    is Ping -> sendBaseModel(Ping())
                }
            }
        }
    }

    fun sendBaseModel(data: BaseModel) {
        viewModelScope.launch(dispatchers.io) {
            drawingApi.sendBaseModel(data)
        }
    }
}
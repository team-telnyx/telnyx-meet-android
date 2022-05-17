package com.telnyx.meet.ui

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.telnyx.meet.BaseViewModel
import com.telnyx.meet.data.RoomService
import com.telnyx.meet.data.model.RefreshTokenInfo
import com.telnyx.meet.data.model.RoomDetails
import com.telnyx.meet.data.model.TokenInfo
import com.telnyx.meet.data.model.request.CreateRoomRequest
import com.telnyx.meet.data.model.request.CreateTokenRequest
import com.telnyx.meet.data.model.request.RefreshTokenRequest
import com.telnyx.meet.data.model.response.*
import com.telnyx.meet.ui.models.MessageUI
import com.telnyx.meet.ui.utilities.getCurrentTimeHHmm
import com.telnyx.meet.ui.utilities.randomInt
import com.telnyx.video.sdk.Event
import com.telnyx.video.sdk.Room
import com.telnyx.video.sdk.webSocket.model.ui.*
import com.telnyx.video.sdk.model.AudioDevice
import com.telnyx.video.sdk.utilities.PublishConfigHelper
import com.telnyx.video.sdk.utilities.State
import com.telnyx.video.sdk.webSocket.model.send.ExternalData
import com.telnyx.video.sdk.webSocket.model.ui.*
import dagger.hilt.android.lifecycle.HiltViewModel
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.SurfaceViewRenderer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.util.*
import javax.inject.Inject

enum class MediaOnStart {
    ENABLED,
    DISABLED
}

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val roomService: RoomService,
    private val gson: Gson,
    private val context: Context
) : BaseViewModel() {
    private var messageHistory: MutableList<MessageUI>? = mutableListOf()
    private var mTotalPages: Int = 0
    private var mLastRetrievedPage: Int = 1
    private val roomsListRetrieved: MutableLiveData<List<RoomDetails>> = MutableLiveData()
    private val roomCreated: MutableLiveData<RoomDetails> = MutableLiveData()
    private val tokenCreated: MutableLiveData<Event<TokenInfo>> = MutableLiveData()
    private val tokenRefreshed: MutableLiveData<Event<RefreshTokenInfo>> = MutableLiveData()
    private val loading: MutableLiveData<Boolean> = MutableLiveData()
    private val tokenCreationError: MutableLiveData<Boolean> = MutableLiveData()
    val permissionRequest = MutableLiveData<Boolean>()
    var micInitialState: MediaOnStart = MediaOnStart.DISABLED
    var cameraInitialState: MediaOnStart = MediaOnStart.DISABLED
    private var cameraCurrentState: MediaOnStart = MediaOnStart.DISABLED
    private var micCurrentState: MediaOnStart = MediaOnStart.DISABLED

    private val errorMutable: MutableLiveData<Event<CreateRoomErrorResponse>> =
        MutableLiveData<Event<CreateRoomErrorResponse>>()

    private val deletedMutable: MutableLiveData<Event<Boolean>> =
        MutableLiveData<Event<Boolean>>()

    fun error(): LiveData<Event<CreateRoomErrorResponse>> = errorMutable
    fun deleted(): LiveData<Event<Boolean>> = deletedMutable

    lateinit var room: Room

    fun loading(): LiveData<Boolean> = loading

    fun roomListObservable(): LiveData<List<RoomDetails>> = roomsListRetrieved

    fun roomCreatedObservable(): LiveData<RoomDetails> = roomCreated

    fun tokenCreatedObservable(): LiveData<Event<TokenInfo>> = tokenCreated

    fun tokenRefreshedObservable(): LiveData<Event<RefreshTokenInfo>> = tokenRefreshed

    fun tokenCreationErrorObservable() = tokenCreationError

    fun connectedToRoomObservable(): LiveData<Boolean> = room.getConnectionStatus()

    fun getParticipants(): MutableLiveData<MutableList<Participant>> =
        room.getParticipantsObservable()

    fun getTextRoomLeavingParticipant(): MutableLiveData<Event<Participant>> =
        room.getParticipantLeftTextRoomObservable()

    fun getTextRoomMessageDeliverySuccess(): MutableLiveData<Event<Message>> =
        room.getTextRoomMessageDeliverySuccess()

    fun getTextRoomMessageDeliveryFailed(): MutableLiveData<Event<Message>> =
        room.getTextRoomMessageDeliveryFailed()

    fun getTextRoomMessageReceived(): MutableLiveData<Event<Pair<String, Message>>> =
        room.getTextRoomMessageReceived()

    fun getStateChange(): MutableLiveData<State> = room.getStateObservable()

    fun getJoinedRoom(): MutableLiveData<Event<RoomUI>> =
        room.getJoinedRoomObservable()

    fun getJoinedParticipant(): MutableLiveData<Event<Participant>> =
        room.getJoinedParticipant()

    fun getLeavingParticipantId(): MutableLiveData<Pair<Long, String>> =
        room.getLeavingParticipantId()

    fun getSpeakingParticipant(): MutableLiveData<Pair<Participant, String?>> =
        room.getParticipantTalking()

    fun getParticipantStreamChanged(): MutableLiveData<Event<Participant>> =
        room.getParticipantStreamChanged()

    fun getLastRetrievedPage(): Int = mLastRetrievedPage

    fun createTokenForRoom(roomId: String, participantName: String) {
        loading.postValue(true)
        roomService.createClientToken(roomId, CreateTokenRequest())
            .enqueue(object : Callback<CreateTokenResponse> {
                override fun onResponse(
                    call: Call<CreateTokenResponse>,
                    response: Response<CreateTokenResponse>
                ) {
                    loading.postValue(false)
                    if (response.isSuccessful) {
                        response.body()?.tokenInfo?.let { tokenInfo ->
                            // room.setClientToken(it.token)
                            tokenCreated.postValue(Event(tokenInfo))
                            // I have a token, I can create a room instance
                            room = Room(
                                context = context,
                                roomId = UUID.fromString(roomId),
                                roomToken = tokenInfo.token,
                                externalData = ExternalData(randomInt(7), participantName),
                                enableMessages = true
                            )
                        }
                        tokenCreationError.postValue(false)
                    } else {
                        Timber.e("Failed to create token for specified Room ID")
                        tokenCreationError.postValue(true)
                    }
                }

                override fun onFailure(call: Call<CreateTokenResponse>, t: Throwable) {
                    loading.postValue(false)
                    Timber.e(t, "onFailure")
                }
            })
    }

    fun refreshTokenForRoom(roomId: String, refreshToken: String) {
        loading.postValue(true)
        roomService.refreshClientToken(roomId, RefreshTokenRequest(refreshToken))
            .enqueue(object : Callback<RefreshTokenResponse> {
                override fun onResponse(
                    call: Call<RefreshTokenResponse>,
                    response: Response<RefreshTokenResponse>
                ) {
                    loading.postValue(false)
                    if (response.isSuccessful) {
                        response.body()?.refreshTokenInfo?.let {
                            room.setClientToken(it.token)
                            tokenRefreshed.postValue(Event(it))
                        }
                    }
                }

                override fun onFailure(call: Call<RefreshTokenResponse>, t: Throwable) {
                    loading.postValue(false)
                    Timber.e(t, "onFailure")
                }
            })
    }

    fun createNewRoom(roomName: String) {
        loading.postValue(true)
        roomService.createRoom(CreateRoomRequest(unique_name = roomName))
            .enqueue(object : Callback<CreateRoomResponse> {
                override fun onResponse(
                    call: Call<CreateRoomResponse>,
                    response: Response<CreateRoomResponse>
                ) {
                    loading.postValue(false)
                    if (response.isSuccessful) {
                        response.body()?.roomDetails?.let {
                            roomCreated.postValue(it)
                        }
                    } else { // TODO enhance this class after API video team works on Error structure
                        response.errorBody()?.string().let {
                            val error =
                                gson.fromJson(it, CreateRoomErrorResponse::class.java)
                            errorMutable.value = Event(error)
                        }
                    }
                }

                override fun onFailure(call: Call<CreateRoomResponse>, t: Throwable) {
                    loading.postValue(false)
                    Timber.e(t, "onFailure")
                }
            })
    }

    fun getMoreRooms() {
        if (mLastRetrievedPage < mTotalPages) {
            getRoomList(mLastRetrievedPage + 1) // Get one more page
        }
    }

    fun getRoomList(page: Int = 1) {
        loading.postValue(true)
        roomService.getRoomList(page)
            .enqueue(object : Callback<RoomsListResponse> {
                override fun onResponse(
                    call: Call<RoomsListResponse>,
                    response: Response<RoomsListResponse>
                ) {
                    loading.postValue(false)
                    if (response.isSuccessful) {
                        response.body()?.meta?.let {
                            mTotalPages = it.total_pages
                            mLastRetrievedPage = it.page_number
                        }
                        response.body()?.roomDetails.let {
                            roomsListRetrieved.postValue(it)
                        }
                    }
                }

                override fun onFailure(call: Call<RoomsListResponse>, t: Throwable) {
                    loading.postValue(false)
                    Timber.e(t, "onFailure")
                }
            })
    }

    fun checkPermissions(context: Context) {
        Dexter.withContext(context)
            .withPermissions(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        permissionRequest.value = true
                    } else if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            context,
                            "Camera and Audio permissions are required to continue",
                            Toast.LENGTH_LONG
                        ).show()
                        permissionRequest.value = false
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    token?.continuePermissionRequest()
                }
            }).check()
    }

    fun disconnect() {
        room.disconnect()
    }

    fun publish(publishConfigHelper: PublishConfigHelper) {
        room.addStream(publishConfigHelper)
    }

    fun updateStream(publishConfigHelper: PublishConfigHelper) {
        room.updateStream(publishConfigHelper)
    }

    fun connectToRoom() {
        room.connect()
    }

    fun createAudioOutputSelectionDialog(roomFragment: RoomFragment): Dialog {
        return roomFragment.activity.let {
            val audioOutputList = arrayOf("Phone", "Bluetooth", "Loud Speaker")
            val builder = AlertDialog.Builder(roomFragment.requireContext())
            // Set default to phone
            room.setAudioOutputDevice(AudioDevice.PHONE_EARPIECE)
            builder.setTitle("Select Audio Output")
            builder.setSingleChoiceItems(
                audioOutputList, 0
            ) { _, which ->
                when (which) {
                    0 -> {
                        room.setAudioOutputDevice(AudioDevice.PHONE_EARPIECE)
                    }
                    1 -> {
                        room.setAudioOutputDevice(AudioDevice.BLUETOOTH)
                    }
                    2 -> {
                        room.setAudioOutputDevice(AudioDevice.LOUDSPEAKER)
                    }
                }
            }
                // Set the action buttons
                .setNeutralButton(
                    "ok"
                ) { dialog, _ ->
                    dialog.dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    fun changeAudioOutput(audioDevice: AudioDevice) {
        room.setAudioOutputDevice(audioDevice)
    }

    fun deleteRoom(roomId: String) {
        roomService.deleteRoom(roomId)
            .enqueue(object : Callback<Void> {
                override fun onResponse(
                    call: Call<Void>,
                    response: Response<Void>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Room was deleted",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Timber.e(t, "onFailure")
                }
            })
    }

    fun setParticipantSurface(
        participantId: String,
        participantSurface: SurfaceViewRenderer,
        streamKey: String
    ) {
        room.setParticipantSurface(participantId, participantSurface, streamKey)
    }

    fun saveChatHistory(messages: MutableList<MessageUI>?) {
        messageHistory = messages
    }

    fun getChatHistory() = messageHistory

    fun sendTextRoomMessage(message: Message) {
        room.sendTextRoomMessage(message)
    }

    fun stopObserveRoomCreatedValue() {
        roomCreated.postValue(null)
    }

    fun createAdminMessage(adminMessageText: String) = MessageUI(
        date = getCurrentTimeHHmm(),
        sender = "ADMIN",
        fullMessage = Message(
            message = MessageContent(type = MessageType.TEXT, payload = adminMessageText, null),
            recipients = emptyList()
        )
    )

    /**
     * This method is to save message on the messageHistory when chat is not displayed
     */
    fun addMessageToHistory(messageForHistory: MessageUI) {
        messageHistory?.add(messageForHistory)
    }

    fun getStateTextFile(): String? {
        return room.getStateTextFile()
    }

    fun subscribe(
        participantId: String,
        streamKey: String,
        streamConfig: StreamConfig
    ) {
        room.addSubscription(participantId, streamKey, streamConfig)
    }

    fun unsubscribe(participantId: String, streamKey: String) {
        room.removeSubscription(participantId, streamKey)
    }

    fun clearChatHistory() {
        messageHistory?.clear()
    }

    fun getWebRTCStatsForStream(
        participantId: String,
        streamKey: String,
        callback: RTCStatsCollectorCallback
    ) {
        room.getWebRTCStatsForStream(participantId, streamKey, callback)
    }

    fun enableNetworkMetricsReport(participantsList: List<String>) {
        room.enableNetworkMetricsReport(participantsList)
    }

    fun disableNetworkMetricsReport(participantsList: List<String>) {
        room.disableNetworkMetricsReport(participantsList)
    }

    fun setCameraState(media: MediaOnStart) {
        cameraCurrentState = media
    }

    fun getCameraState(): MediaOnStart {
        return cameraCurrentState
    }

    fun setMicState(media: MediaOnStart) {
        micCurrentState = media
    }

    fun getMicState(): MediaOnStart {
        return micCurrentState
    }
}

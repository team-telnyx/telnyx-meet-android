package com.telnyx.meet.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.google.common.util.concurrent.ListenableFuture
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.navigator.Navigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.room_join_fragment.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class JoinRoomFragment @Inject constructor(
    val navigator: Navigator
) : BaseFragment() {
    private var participantName: String = ""
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val roomsViewModel: RoomsViewModel by activityViewModels()
    private var permissionsGranted = false
    override val layoutId: Int = R.layout.room_join_fragment
    private var toggledVideo = false
    private var toggledAudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            requireActivity().finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        roomsViewModel.clearChatHistory()
        roomsViewModel.checkPermissions(requireActivity())

        (activity as ManageRoomActivity).setActionBarTitle("Telnyx Video Rooms")

        roomsViewModel.permissionRequest.observe(this.viewLifecycleOwner) { permission ->
            permissionsGranted = permission
        }
        roomsViewModel.tokenCreationErrorObservable().postValue(false)
        buttonJoinRoom.setOnClickListener {
            if (permissionsGranted) {
                navigateToRoomCreate(room_uuid_et.text.toString())
            } else {
                roomsViewModel.checkPermissions(requireActivity())
            }
        }
        see_available_rooms_text.setOnClickListener {
            navigateToRoomList()
        }

        toggleCameraButton.setOnClickListener {
            toggledVideo = if (!toggledVideo) {
                toggleCameraButton.setImageResource(R.drawable.ic_camera_off)
                startCamera()
                roomsViewModel.cameraInitialState = MediaOnStart.ENABLED
                true
            } else {
                toggleCameraButton.setImageResource(R.drawable.ic_camera)
                stopCamera()
                roomsViewModel.cameraInitialState = MediaOnStart.DISABLED
                false
            }
        }
        toggleAudioButton.setOnClickListener {
            toggledAudio = if (!toggledAudio) {
                toggleAudioButton.setImageResource(R.drawable.ic_mic_off)
                roomsViewModel.micInitialState = MediaOnStart.ENABLED
                true
            } else {
                toggleAudioButton.setImageResource(R.drawable.ic_mic)
                roomsViewModel.micInitialState = MediaOnStart.DISABLED
                false
            }
        }
        participantName = getString(R.string.dummy_android_participant_name)
        participant_name_et.addTextChangedListener {
            participantName = it.toString()
        }
    }

    private fun navigateToRoomList() {
        if (!participantName.isEmpty()) {
            val action = com.telnyx.meet.ui.JoinRoomFragmentDirections.joinRoomToRoomListFragment(
                participantName
            )
            action.participantName = participantName
            navigator.navController.navigate(action)
        } else {
            participant_name_it.error = getString(R.string.provide_participant_name_error)
        }
    }

    private fun navigateToRoomCreate(roomId: String? = null) {
        if (participantName.isNotEmpty()) {
            if (!roomId.isNullOrEmpty()) {
                val action =
                    com.telnyx.meet.ui.JoinRoomFragmentDirections.joinRoomToRoomCreateFragment(
                        participantName
                    )
                action.roomId = roomId
                action.roomName = roomId
                navigator.navController.navigate(action)
            } else {
                Toast.makeText(requireContext(), "Unable to navigate to room", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            participant_name_it.error = getString(R.string.provide_participant_name_error)
        }
    }

    private fun startCamera() {
        this.context?.let { context ->
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture?.let { cameraProviderFuture ->
                cameraProviderFuture.addListener({
                    // Used to bind the lifecycle of cameras to the lifecycle owner

                    cameraProvider = cameraProviderFuture.get()

                    // Preview
                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewLoginView.surfaceProvider)
                        }

                    // Select back camera as a default
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        // Unbind use cases before rebinding
                        cameraProvider?.unbindAll()

                        // Bind use cases to camera
                        cameraProvider?.bindToLifecycle(
                            this.viewLifecycleOwner, cameraSelector, preview
                        )
                    } catch (exc: Exception) {
                        Timber.tag("RoomFragment").d("Starting refresh")
                    }
                }, ContextCompat.getMainExecutor(context))
                }
                previewPlaceHolder.visibility = View.GONE
                previewLoginView.visibility = View.VISIBLE
            }
        }

        private fun stopCamera() {
            previewPlaceHolder.visibility = View.VISIBLE
            previewLoginView.visibility = View.GONE
            cameraProvider?.unbindAll()
        }
    }
    
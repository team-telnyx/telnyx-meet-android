package com.telnyx.meet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.telnyx.meet.BuildConfig
import com.telnyx.meet.R
import com.telnyx.meet.databinding.RoomJoinFragmentBinding
import com.telnyx.meet.navigator.Navigator
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class JoinRoomFragment @Inject constructor(
    val navigator: Navigator
) : BaseFragment<RoomJoinFragmentBinding>() {

    private var participantName: String = ""
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val roomsViewModel: RoomsViewModel by activityViewModels()
    private var permissionsGranted = false
    override val layoutId: Int = R.layout.room_join_fragment
    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): RoomJoinFragmentBinding {
        return RoomJoinFragmentBinding.inflate(inflater, container, false)
    }

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
        binding.roomUuidEt.setText(BuildConfig.DEFAULT_ROOM)
        roomsViewModel.tokenCreationErrorObservable().postValue(false)
        binding.apply {
            buttonJoinRoom.setOnClickListener {
                if (permissionsGranted) {
                    navigateToRoomCreate(binding.roomUuidEt.text.toString())
                } else {
                    roomsViewModel.checkPermissions(requireActivity())
                }
            }
            binding.seeAvailableRoomsText.setOnClickListener {
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
            participantNameEt.addTextChangedListener {
                participantName = it.toString()
            }
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
           binding.participantNameIt.error = getString(R.string.provide_participant_name_error)
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
            binding.participantNameIt.error = getString(R.string.provide_participant_name_error)
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
                            it.setSurfaceProvider(binding.previewLoginView.surfaceProvider)
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
                binding.previewPlaceHolder.visibility = View.GONE
                binding.previewLoginView.visibility = View.VISIBLE
            }
        }

        private fun stopCamera() {
            binding.previewPlaceHolder.visibility = View.VISIBLE
            binding.previewLoginView.visibility = View.GONE
            cameraProvider?.unbindAll()
        }
    }
    
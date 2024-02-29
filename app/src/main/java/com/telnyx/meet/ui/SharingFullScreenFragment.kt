package com.telnyx.meet.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.databinding.SharingFullScreenBinding
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.utilities.hideSystemUI
import com.telnyx.meet.ui.utilities.isFullScreenEnabled
import com.telnyx.meet.ui.utilities.showSystemUI
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SharingFullScreenFragment @Inject constructor(
    val navigator: Navigator,
) : BaseFragment<SharingFullScreenBinding>() {

    private var participantSharing: Participant? = null
    private var fullScreenSurface: SurfaceViewRenderer? = null
    private var fullScreenEnforcerJob: Job? = null

    private var fragmentView: View? = null
    val roomsViewModel: RoomsViewModel by activityViewModels()

    override val layoutId: Int = R.layout.sharing_full_screen
    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): SharingFullScreenBinding {
        return SharingFullScreenBinding.inflate(inflater, container, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (fragmentView == null)
            fragmentView = inflater.inflate(
                R.layout.sharing_full_screen,
                container, false
            )
        return fragmentView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            removeSharingSurface()
            activity?.showSystemUI()
            navigator.navigateBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fullScreenSurface = binding.mainFullScreenSurface
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        startFullScreenEnforcer()
        setObservers()
    }

    override fun onResume() {
        super.onResume()
        Timber.tag("SharingFullFragment")
            .d("onResume surface: ${binding?.mainFullScreenSurface.hashCode()}")
    }

    private fun setObservers() {
        roomsViewModel.getParticipants().observe(viewLifecycleOwner) { participants ->
            participants.let { participantList ->
                participantList.find { it.streams.find { stream -> stream.streamKey == "presentation" } != null }
                    ?.let { participant ->
                        val sharingTrack =
                            participant.streams.find { it.streamKey == "presentation" }

                        if (sharingTrack?.videoEnabled == StreamStatus.ENABLED) {
                            participantSharing = participant
                            removeSharingSurface()
                            Timber.tag("SharingFullFragment")
                                .d("addingSurface surface: ${binding.mainFullScreenSurface.hashCode()}")
                            roomsViewModel.setParticipantSurface(
                                participant.participantId,
                                binding.mainFullScreenSurface,
                                "presentation"
                            )
                            sharingTrack.videoTrack?.addSink(binding.mainFullScreenSurface)
                            sharingTrack.videoTrack?.setEnabled(true)
                        }
                    }
            }
        }

        roomsViewModel.getLeavingParticipantId()
            .observe(viewLifecycleOwner) { participantLeavingId ->
                Timber.tag("SharingFullFragment")
                    .d("Participant Leaving :: $participantLeavingId")
                participantLeavingId?.let { (id, reason) ->
                    if (participantSharing?.id == id) {
                        // Stopped sharing
                        navigator.navigateBack()
                    }
                }
            }

        roomsViewModel.getParticipantStreamChanged()
            .observe(viewLifecycleOwner) { participantChangedStreamsEvent ->
                participantChangedStreamsEvent.getContentIfNotHandled()
                    ?.let { participantChangedStreams ->
                        Timber.tag("SharingFullFragment")
                            .d("Participant $participantChangedStreams stream changed")
                        if ((participantSharing?.participantId == participantChangedStreams.participantId)) {
                            // Check if still sharing
                            if (participantChangedStreams.streams.find { it.streamKey == "presentation" }?.videoEnabled != StreamStatus.ENABLED) {
                                // Stopped sharing
                                navigator.navigateBack()
                            }
                        }
                    }
            }
    }

    private fun startFullScreenEnforcer() {
        removeSharingSurface()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.tag("SharingFullFragment").d("CoroutineExceptionHandler: $exception")
        }
        fullScreenEnforcerJob = CoroutineScope(Dispatchers.Main).launch(handler) {
            while (isActive) {
                delay(4000)
                if (activity?.isFullScreenEnabled() == false)
                    activity?.hideSystemUI()
            }
        }
    }

    private fun removeSharingSurface() {
        Timber.tag("SharingFullFragment").d("remove surface: ${fullScreenSurface?.hashCode()}")
        participantSharing?.let { sharingParticipant ->
            fullScreenSurface?.let { fullSurface ->
                sharingParticipant.streams.find { it.streamKey == "presentation" }?.videoTrack?.removeSink(
                    fullSurface
                )
                Timber.tag("SharingFullFragment").d("Full releasing")
                fullSurface.release()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeSharingSurface()
    }
}

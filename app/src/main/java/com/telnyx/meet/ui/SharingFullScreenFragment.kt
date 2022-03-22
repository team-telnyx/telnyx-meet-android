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
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.utilities.hideSystemUI
import com.telnyx.meet.ui.utilities.isFullScreenEnabled
import com.telnyx.meet.ui.utilities.showSystemUI
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.sharing_full_screen.*
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class SharingFullScreenFragment @Inject constructor(
    val navigator: Navigator,
) : BaseFragment() {

    private var participantSharing: Participant? = null
    private var fullScreenEnforcerJob: Job? = null

    private var fragmentView: View? = null
    val roomsViewModel: RoomsViewModel by activityViewModels()

    override val layoutId: Int = R.layout.sharing_full_screen

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
            activity?.showSystemUI()
            roomsViewModel.shouldAcceptFirstSharingInfo = true
            navigator.navigateBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        startFullScreenEnforcer()
        setObservers()
    }

    private fun setObservers() {
        roomsViewModel.getParticipantSharingChanged()
            .observe(viewLifecycleOwner) { participantSharingEvent ->
                participantSharingEvent.peekContent().let { participantSharing ->
                    when (participantSharing.sharingEnabled) {
                        StreamStatus.ENABLED -> {
                            participantSharing.sharingTrack?.addSink(mainFullScreenSurface)
                            participantSharing.sharingTrack?.setEnabled(true)
                            roomsViewModel.setParticipantSurface(
                                participantSharing.participantId,
                                mainFullScreenSurface,
                                true
                            )
                        }
                        else -> {
                            roomsViewModel.shouldAcceptFirstSharingInfo = true
                            navigator.navigateBack()
                        }
                    }
                }
            }

        roomsViewModel.getLeavingParticipantId()
            .observe(viewLifecycleOwner) { participantLeavingId ->
                participantLeavingId?.let {
                    if (participantSharing?.sharingId == it.first) {
                        roomsViewModel.shouldAcceptFirstSharingInfo = true
                        navigator.navigateBack()
                    }
                }
            }
    }

    private fun startFullScreenEnforcer() {
        fullScreenEnforcerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(4000)
                if (activity?.isFullScreenEnabled() == false)
                    activity?.hideSystemUI()
            }
        }
    }
}

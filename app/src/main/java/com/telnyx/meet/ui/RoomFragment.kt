package com.telnyx.meet.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.view.menu.MenuBuilder
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Severity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.GsonBuilder
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.adapters.ParticipantTileAdapter
import com.telnyx.meet.ui.adapters.ParticipantTileListener
import com.telnyx.meet.ui.models.*
import com.telnyx.meet.ui.utilities.calculateTokenExpireTime
import com.telnyx.meet.ui.utilities.getCurrentTimeHHmm
import com.telnyx.video.sdk.filter.TelnyxVideoProcessing
import com.telnyx.video.sdk.utilities.CameraDirection
import com.telnyx.video.sdk.utilities.PublishConfigHelper
import com.telnyx.video.sdk.utilities.StateAction
import com.telnyx.video.sdk.webSocket.model.receive.PluginDataBody
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamConfig
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.room_fragment.*
import kotlinx.coroutines.*
import org.webrtc.RTCStatsReport
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RoomFragment @Inject constructor(
    val navigator: Navigator,
) : BaseFragment(), ParticipantTileListener {

    companion object RoomFragmentConstants {
        private const val FULL_PARTICIPANT_LIST = 8
        private const val SHARING_PARTICIPANT_LIST = 4

        private const val VIDEO_TRACK_KEY = "000"
        private const val AUDIO_TRACK_KEY = "001"
        const val SELF_STREAM_KEY = "self"

        private enum class ViewMode {
            FULL_PARTICIPANTS,
            MAIN_SHARE_VIEW_AND_PARTICIPANTS,
            MAIN_SHARE_VIEW_ONLY
        }

        private enum class CapturerConstraints(val value: Int) {
            // 720p at 30 fps
            WIDTH(256),
            HEIGHT(256),
            FPS(30)
        }
    }

    private var updateAdapterEnabled: Boolean = true
    private val SELF_STREAM_ID = UUID.randomUUID().toString()
    private var menu: Menu? = null
    private var selfSurface: SurfaceViewRenderer? = null
    private var mStatsDialog: BottomSheetDialog? = null
    private var mStatsParticipant: StatsParticipant? = null
    private var networkQualityList = mutableListOf<String>()

    private var mSharingParticipant: Participant? = null
    private var currentViewMode: ViewMode = ViewMode.FULL_PARTICIPANTS
    private var selfParticipantId: String? = null
    private var selfParticipantHandleId: Long? = null
    private var mRefreshTokenJob: Job? = null
    private var mStatsjob: Job? = null
    private lateinit var mRoomName: String
    private lateinit var mRoomId: String
    private lateinit var mRefreshToken: String
    private var mRefreshTimer: Int = 0
    val roomsViewModel: RoomsViewModel by activityViewModels()

    private var videoProcessing: TelnyxVideoProcessing? = null
    private var toggledVideo = false
    private var toggledAudio = false
    private var toggledBlur = false
    private var toggledVirtualBackground = false
    private var toggleNetworkQuality = false
    private var speakerViewEnabled = false
    private var isFullShare = false

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var publishConfigHelper: PublishConfigHelper? = null

    override val layoutId: Int = R.layout.room_fragment

    private val participantAdapter: ParticipantTileAdapter by lazy {
        val adapter = ParticipantTileAdapter(this)
        adapter.setHasStableIds(true)
        adapter
    }

    private val args: com.telnyx.meet.ui.RoomFragmentArgs by navArgs()

    private var fragmentView: View? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        if (fragmentView == null)
            fragmentView = inflater.inflate(
                R.layout.room_fragment,
                container, false
            )
        return fragmentView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startCounter()
        mRoomId = args.roomId
        mRoomName = args.roomName
        mRefreshToken = args.refreshToken
        mRefreshTimer = args.refreshTime
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            goBack()
        }
        setHasOptionsMenu(true)
    }

    private fun goBack(wasKicked: Boolean = false) {
        if (toggledVideo) {
            stopCameraCapture()
        }
        if (toggledAudio) {
            stopAudioCapture()
        }
        if (!wasKicked) {
            roomsViewModel.disconnect()
        }
        navigator.navigate(R.id.roomFragmentToJoinRoomFragment)
        mRefreshTokenJob?.cancel()
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.room_menu, menu)
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        this.menu = menu
    }

    private fun startCounter() {
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.tag("RoomFragment")
                .d("CoroutineExceptionHandler refresh token counter: $exception")
        }
        mRefreshTokenJob = CoroutineScope(Dispatchers.Main).launch(handler) {
            if (isActive) {
                Timber.tag("RoomFragment").d("Starting counter")
                delay(mRefreshTimer.toLong())
                Timber.tag("RoomFragment").d("Starting refresh")
                roomsViewModel.refreshTokenForRoom(mRoomId, mRefreshToken)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_camera -> {
                toggledVideo = if (!toggledVideo) {
                    item.icon = context?.getDrawable(R.drawable.ic_camera_white)
                    startCameraCapture()
                    true
                } else {
                    item.icon = context?.getDrawable(R.drawable.ic_camera_off_white)
                    stopCameraCapture()
                    false
                }
                true
            }

            R.id.action_mic -> {
                toggledAudio = if (!toggledAudio) {
                    item.icon = context?.getDrawable(R.drawable.ic_mic_white)
                    startAudioCapture()
                    true
                } else {
                    item.icon = context?.getDrawable(R.drawable.ic_mic_off_white)
                    stopAudioCapture()
                    false
                }
                true
            }

            R.id.action_chat -> {
                val action =
                    com.telnyx.meet.ui.RoomFragmentDirections.roomFragmentToRoomChatFragment()
                navigator.navController.navigate(action)
                true
            }

            R.id.action_flip_camera -> {
                publishConfigHelper?.changeCameraDirection()
                true
            }

            R.id.action_blur -> {
                if (!toggledBlur && !toggledVirtualBackground) {
                    publishConfigHelper?.let {
                        applyBlurFilter(it)
                        toggledBlur = true
                    } ?: run {
                        Timber.tag("RoomFragment")
                            .d("There is no camera stream to apply filter to or existing filter in use")
                    }
                } else {
                    removeFilter()
                    toggledBlur = false
                }
                true
            }

            R.id.action_virtual_background -> {
                if (!toggledVirtualBackground && !toggledBlur) {
                    publishConfigHelper?.let {
                        applyBackgroundFilter(it)
                        toggledVirtualBackground = true
                    } ?: run {
                        Timber.tag("RoomFragment")
                            .d("There is no camera stream to apply filter to or existing filter in use")
                    }
                } else {
                    removeFilter()
                    toggledVirtualBackground = false
                }
                true
            }

            R.id.action_mic_source -> {
                val audioDialog = roomsViewModel.createAudioOutputSelectionDialog(this)
                audioDialog.show()
                true
            }

            R.id.action_main_view -> {
                when (currentViewMode) {
                    ViewMode.FULL_PARTICIPANTS -> {
                        adaptUIToMainView()
                    }
                    ViewMode.MAIN_SHARE_VIEW_AND_PARTICIPANTS -> {
                        adaptUIToFullParticipants()
                    }
                    ViewMode.MAIN_SHARE_VIEW_ONLY -> {
                        adaptUIToFullShareMode()
                    }
                }
                true
            }

            R.id.action_view_participants -> {
                val action =
                    com.telnyx.meet.ui.RoomFragmentDirections.actionRoomFragmentToRoomParticipantsFragment()
                navigator.navController.navigate(action)
                true
            }

            R.id.enable_nq_metrics -> {
                toggleNetworkQuality = if (!toggleNetworkQuality) {
                    item.icon = context?.getDrawable(R.drawable.ic_network_quality_on)
                    startNetworkQualityCapture()
                    true
                } else {
                    item.icon = context?.getDrawable(R.drawable.ic_network_quality_off)
                    stopNetworkQualityCapture()
                    false
                }
                true
            }

            R.id.action_report_issue -> {
                showIssueDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stopNetworkQualityCapture() {
        roomsViewModel.disableNetworkMetricsReport(networkQualityList)
    }

    private fun startNetworkQualityCapture() {
        roomsViewModel.enableNetworkMetricsReport(networkQualityList)
    }

    private fun stopAudioCapture() {
        publishConfigHelper?.let {
            it.disposeAudio()
            roomsViewModel.updateStream(it)
        }
    }

    private fun startAudioCapture() {
        val shouldPublish = publishConfigHelper == null
        if (shouldPublish) {
            publishConfigHelper = PublishConfigHelper(
                requireContext(),
                CameraDirection.FRONT,
                SELF_STREAM_KEY,
                SELF_STREAM_ID
            )
        }
        publishConfigHelper?.let {
            it.createAudioTrack(true, AUDIO_TRACK_KEY)
            if (shouldPublish) {
                roomsViewModel.publish(it)
            } else {
                roomsViewModel.updateStream(it)
            }
        }
    }

    private fun stopCameraCapture() {
        publishConfigHelper?.let {
            it.stopCapture()
            roomsViewModel.updateStream(it)
            selfSurface?.let { surface -> it.releaseSurfaceView(surface) }
        }
    }

    private fun startCameraCapture() {
        val shouldPublish = publishConfigHelper == null
        if (shouldPublish) {
            publishConfigHelper = PublishConfigHelper(
                requireContext(),
                CameraDirection.FRONT,
                SELF_STREAM_KEY,
                SELF_STREAM_ID
            )
        }
        selfSurface?.let { publishConfigHelper?.setSurfaceView(it) }
        publishConfigHelper?.let {
            it.createVideoTrack(
                CapturerConstraints.WIDTH.value,
                CapturerConstraints.HEIGHT.value,
                CapturerConstraints.FPS.value, true, VIDEO_TRACK_KEY
            )
            if (shouldPublish) {
                roomsViewModel.publish(it)
            } else {
                roomsViewModel.updateStream(it)
            }
        }
    }

    private fun applyBackgroundFilter(publishConfigHelper: PublishConfigHelper) {
        videoProcessing = TelnyxVideoProcessing(this.requireContext(), publishConfigHelper)
        videoProcessing?.applyVirtualBackground("clouds.jpeg")
    }

    private fun applyBlurFilter(publishConfigHelper: PublishConfigHelper) {
        videoProcessing = TelnyxVideoProcessing(this.requireContext(), publishConfigHelper)
        videoProcessing?.applyBackgroundBlur()
    }

    private fun removeFilter() {
        videoProcessing?.removeImageProcess()
    }

    private fun showIssueDialog() {
        val stateFile = roomsViewModel.getStateTextFile()
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Issue Description")
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
            val issueDescription = input.text.toString()
            Bugsnag.notify(RuntimeException("Report Issue pressed")) { event ->
                event.addMetadata("SDK State", "SDK State", stateFile)
                event.addMetadata("Issue Details", "Room ID", mRoomId)
                event.addMetadata("Issue Details", "Room Name", mRoomName)
                event.addMetadata("Issue Details", "Participant ID", selfParticipantId)
                event.addMetadata("Issue Details", "Issue Description", issueDescription)
                event.severity = Severity.INFO
                true
            }
            Toast.makeText(requireContext(), "Issue Reported!", Toast.LENGTH_LONG).show()
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun adaptUIToFullParticipants() {
        Timber.tag("RoomFragment").d("adaptUIToFullParticipants")
        currentViewMode = ViewMode.FULL_PARTICIPANTS
        participantAdapter.setMaxParticipants(FULL_PARTICIPANT_LIST)
        setRecyclerLayoutParams(ViewMode.FULL_PARTICIPANTS)
        mainCardSurface.visibility = View.GONE
        bzoneLine.setGuidelinePercent(0.05f)
    }

    private fun adaptUIToMainView() {
        Timber.tag("RoomFragment").d("adaptUIToMainView")
        currentViewMode = ViewMode.MAIN_SHARE_VIEW_AND_PARTICIPANTS
        participantAdapter.setMaxParticipants(SHARING_PARTICIPANT_LIST)
        setRecyclerLayoutParams(ViewMode.MAIN_SHARE_VIEW_AND_PARTICIPANTS)
        mainCardSurface.visibility = View.VISIBLE
        bzoneLine.setGuidelinePercent(0.5f)
    }

    private fun adaptUIToFullShareMode() {
        mSharingParticipant?.let {
            val action =
                com.telnyx.meet.ui.RoomFragmentDirections.roomFragmentToSharingFullScreenFragment()
            navigator.navController.navigate(action)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        room_id_tv.text = args.roomId

        (activity as ManageRoomActivity).setActionBarTitle(args.roomName)

        fabExtraParticipant.setOnClickListener {
            val action =
                com.telnyx.meet.ui.RoomFragmentDirections.actionRoomFragmentToRoomParticipantsFragment()
            navigator.navController.navigate(action)
        }

        mainSurface.setOnClickListener {
            mSharingParticipant?.participantId?.let { participantId ->
                mStatsParticipant = StatsParticipant(
                    participantId = participantId,
                    isSelf = false,
                    isPresentation = true
                )
                startStatsJob(participantId, "presentation", StatsSource.REMOTE_VIDEO)
            }
        }

        fullScreenButton.setOnClickListener {
            isFullShare = if (!isFullShare) {
                adaptUIToFullShareMode()
                ViewMode.MAIN_SHARE_VIEW_ONLY
                true
            } else {
                adaptUIToMainView()
                ViewMode.MAIN_SHARE_VIEW_AND_PARTICIPANTS
                false
            }
        }

        setupRecyclerView()
        setObservers()
        checkInitialMediaStatus()
    }

    private fun controlledUpdateOfAdapter() {
        if (updateAdapterEnabled) {
            val handler = CoroutineExceptionHandler { _, exception ->
                Timber.tag("RoomFragment").d("CoroutineExceptionHandler adapter update: $exception")
            }
            CoroutineScope(Dispatchers.Main).launch(handler) {
                if (isActive) {
                    updateAdapterEnabled = false
                    participantAdapter.notifyDataSetChanged()
                    delay(1000)
                    updateAdapterEnabled = true
                }
            }
        }
    }

    private fun checkInitialMediaStatus() {
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.tag("RoomFragment").d("CoroutineExceptionHandler media status: $exception")
        }
        CoroutineScope(Dispatchers.Main).launch(handler) {
            if (isActive) {
                delay(300)
                if (roomsViewModel.cameraInitialState == MediaOnStart.ENABLED) {
                    toggledVideo = true
                    menu?.findItem(R.id.action_camera)?.icon =
                        context?.getDrawable(R.drawable.ic_camera_off_white)
                    startCameraCapture()
                }
                if (roomsViewModel.micInitialState == MediaOnStart.ENABLED) {
                    toggledAudio = true
                    menu?.findItem(R.id.action_mic)?.icon =
                        context?.getDrawable(R.drawable.ic_mic_off_white)
                    startAudioCapture()
                }
                if (roomsViewModel.getMicState() == MediaOnStart.ENABLED) {
                    toggledAudio = true
                    menu?.findItem(R.id.action_mic)?.icon =
                        context?.getDrawable(R.drawable.ic_mic_white)
                }
                if (roomsViewModel.getCameraState() == MediaOnStart.ENABLED) {
                    toggledVideo = true
                    menu?.findItem(R.id.action_camera)?.icon =
                        context?.getDrawable(R.drawable.ic_camera_white)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        setRecyclerLayoutParams(currentViewMode)
        participantTileRecycler.adapter = participantAdapter
        val animator = participantTileRecycler.getItemAnimator()
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun setRecyclerLayoutParams(viewMode: ViewMode) {
        Timber.tag("RoomFragment").d("setRecyclerLayoutParms")
        when (viewMode) {
            ViewMode.FULL_PARTICIPANTS -> {
                participantTileRecycler.layoutManager =
                    object : GridLayoutManager(this.context, 2) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            lp.height = (height / 4.2).toInt()
                            lp.width = (width / 2.1).toInt()
                            return true
                        }
                    }
            }
            ViewMode.MAIN_SHARE_VIEW_AND_PARTICIPANTS -> {
                participantTileRecycler.layoutManager =
                    object : GridLayoutManager(this.context, 2) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            lp.height = (height / 2.1).toInt()
                            lp.width = (width / 2.1).toInt()
                            return true
                        }
                    }
            }
            ViewMode.MAIN_SHARE_VIEW_ONLY -> {
                participantTileRecycler.layoutManager =
                    object : GridLayoutManager(this.context, 1) {
                        override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                            lp.height = height
                            lp.width = width
                            return true
                        }
                    }
            }
        }
    }

    private fun setObservers() {
        roomsViewModel.getStateChange().observe(viewLifecycleOwner) { state ->
            Timber.tag("SDKState").d("State: $state")
            when (state.action) {
                StateAction.NETWORK_METRICS_REPORT.action -> controlledUpdateOfAdapter()
            }
        }

        roomsViewModel.getParticipants().observe(viewLifecycleOwner) { participants ->
            participants.let { participantList ->
                participantAdapter.setData(participantList)
                networkQualityList.addAll(
                    participantList.map {
                        it.participantId
                    }
                )

                if (participantList.size > 0) {
                    selfParticipantId = participantList[0].participantId
                    selfParticipantHandleId = participantList[0].id
                }
                participantList.find { it.streams.find { stream -> stream.streamKey == "presentation" } != null }
                    ?.let {
                        processParticipantSharing(it)
                    }
            }
        }

        roomsViewModel.getJoinedParticipant().observe(viewLifecycleOwner) { participantJoined ->
            participantJoined?.let { joinedParticipantEvent ->
                joinedParticipantEvent.getContentIfNotHandled()?.let {
                    participantAdapter.addParticipant(it)
                    networkQualityList.add(it.participantId)
                    if (toggleNetworkQuality) {
                        startNetworkQualityCapture()
                    }
                    if (it.canReceiveMessages) {
                        roomsViewModel.addMessageToHistory(roomsViewModel.createAdminMessage("${it.externalUsername} has joined"))
                    }
                }
            }
        }

        roomsViewModel.getLeavingParticipantId()
            .observe(viewLifecycleOwner) { participantLeavingId ->
                Timber.tag("RoomFragment")
                    .d("Participant Leaving :: $participantLeavingId")
                participantLeavingId?.let { (id, reason) ->
                    if (mSharingParticipant?.id == id) {
                        // Stopped sharing
                        adaptUIToFullParticipants()
                        unsuscribeSharingParticipant()
                    }
                    participantAdapter.removeParticipant(id)
                    Timber.tag("RoomFragment")
                        .d("getLeavingParticipantId :: $participantLeavingId")
                    if (id == selfParticipantHandleId && reason == PluginDataBody.LeftReason.KICKED.reason) {
                        // It's ourselves, remove from the room.
                        goBack(wasKicked = true)
                        Toast.makeText(requireContext(), "You were kicked!", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }

        roomsViewModel.getParticipantStreamChanged()
            .observe(viewLifecycleOwner) { participantChangedStreamsEvent ->
                participantChangedStreamsEvent.getContentIfNotHandled()
                    ?.let { participantChangedStreams ->
                        Timber.tag("RoomFragment")
                            .d("Participant $participantChangedStreams stream changed")
                        val position =
                            participantAdapter.getPositionFromParticipant(participant = participantChangedStreams)
                        participantAdapter.notifyItemChanged(position)
                        if ((mSharingParticipant?.participantId == participantChangedStreams.participantId)) {
                            // Check if still sharing
                            if (participantChangedStreams.streams.find { it.streamKey == "presentation" }?.videoEnabled != StreamStatus.ENABLED) {
                                // Stopped sharing
                                adaptUIToFullParticipants()
                                unsuscribeSharingParticipant()
                            }
                        }
                        if (participantChangedStreams.streams.find { it.streamKey == "presentation" } != null) {
                            processParticipantSharing(participantChangedStreams)
                        }
                    }
            }

        roomsViewModel.getSpeakingParticipant().observe(viewLifecycleOwner) { speakingParticipant ->
            speakingParticipant?.let {
                // controlledUpdateOfAdapter()
                val position = participantAdapter.getPositionFromParticipant(participant = it.first)
                Timber.tag("RoomFragment")
                    .d("Speaking: ${it.first.externalUsername} in position: $position")
                participantAdapter.notifyItemChanged(position)
            }
        }

        roomsViewModel.tokenRefreshedObservable()
            .observe(this.viewLifecycleOwner) { tokenRefreshedEvent ->
                tokenRefreshedEvent.getContentIfNotHandled()?.let { refreshTokenInfo ->
                    mRefreshTimer = calculateTokenExpireTime(refreshTokenInfo.token_expires_at)
                    Timber.tag("RoomFragment").d("Refresh timer set to: $mRefreshTimer")
                    startCounter()
                }
            }

        roomsViewModel.getTextRoomLeavingParticipant()
            .observe(this.viewLifecycleOwner) { participantEvent ->
                participantEvent.getContentIfNotHandled()?.let { participant ->
                    roomsViewModel.addMessageToHistory(roomsViewModel.createAdminMessage("${participant.externalUsername} has left"))
                }
            }

        roomsViewModel.getTextRoomMessageDeliverySuccess()
            .observe(this.viewLifecycleOwner) { messageDeliveredEvent ->
                messageDeliveredEvent.getContentIfNotHandled()?.let {
                    val messageUI =
                        MessageUI(sender = "SELF", fullMessage = it, getCurrentTimeHHmm())
                    roomsViewModel.addMessageToHistory(messageUI)
                }
            }

        roomsViewModel.getTextRoomMessageDeliveryFailed()
            .observe(this.viewLifecycleOwner) { messageNotDeliveredEvent ->
                messageNotDeliveredEvent.getContentIfNotHandled()?.let {
                    Timber.e("Message not delivered: $it")
                }
            }

        roomsViewModel.getTextRoomMessageReceived()
            .observe(this.viewLifecycleOwner) { messageReceivedEvent ->
                messageReceivedEvent.getContentIfNotHandled()?.let { (participantId, fullMessage) ->
                    val messageUI =
                        MessageUI(
                            sender = participantId,
                            fullMessage = fullMessage,
                            getCurrentTimeHHmm()
                        )
                    roomsViewModel.addMessageToHistory(messageUI)
                }
            }
    }

    private fun unsuscribeSharingParticipant() {
        mSharingParticipant?.let {
            roomsViewModel.unsubscribe(it.participantId, "presentation")
        }
        mSharingParticipant = null
    }

    private fun processParticipantSharing(participantSharing: Participant) {
        val presentationStream = participantSharing.streams.find { it.streamKey == "presentation" }
        if (presentationStream?.videoEnabled == StreamStatus.ENABLED) {
            setSharingParticipant(participantSharing)
            if (currentViewMode == ViewMode.FULL_PARTICIPANTS) {
                adaptUIToMainView()
            }
            presentationStream.videoTrack?.addSink(mainSurface)
            presentationStream.videoTrack?.setEnabled(true)
            roomsViewModel.setParticipantSurface(
                participantSharing.participantId,
                mainSurface,
                "presentation"
            )
        }
    }

    private fun startStatsJob(
        participantId: String,
        streamKey: String,
        statsSource: StatsSource
    ) {
        mStatsjob?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.tag("RoomFragment").d("CoroutineExceptionHandler stats job: $exception")
        }
        mStatsjob = CoroutineScope(Dispatchers.Default).launch(handler) {
            while (isActive) {
                getWebRTCStatsForStream(participantId, streamKey, statsSource)
                delay(2000)
            }
        }
    }

    private fun getWebRTCStatsForStream(
        participantId: String,
        streamKey: String,
        statsSource: StatsSource
    ) {
        roomsViewModel.getWebRTCStatsForStream(participantId, streamKey) { statsReport ->
            statsReport?.let { stats ->
                when (statsSource) {
                    StatsSource.REMOTE_VIDEO -> {
                        parseRemoteVideoStats(stats)
                    }
                    StatsSource.REMOTE_AUDIO -> {
                        parseRemoteAudioStats(stats)
                    }
                    StatsSource.LOCAL_VIDEO -> {
                        parseLocalVideoStats(stats)
                    }
                    StatsSource.LOCAL_AUDIO -> {
                        parseLocalAudioStats(stats)
                    }
                }
            }
        }
    }

    private fun parseLocalAudioStats(stats: RTCStatsReport) {
        stats.statsMap.values.filter { it.type == "outbound-rtp" }
            .findLast { it.toString().contains("mediaType: \"audio\"") }
            ?.let { rtcStats ->
                val audioStreamStats =
                    gson.fromJson(
                        rtcStats.toString(),
                        LocalAudioStreamStats::class.java
                    )
                audioStreamStats?.let {
                    showBottomDialog(it)
                }
            }
    }

    private fun parseLocalVideoStats(stats: RTCStatsReport) {
        stats.statsMap.values.filter { it.type == "outbound-rtp" }
            .findLast { it.toString().contains("mediaType: \"video\"") }
            ?.let { rtcVideoStats ->
                val videoStreamStats =
                    gson.fromJson(
                        rtcVideoStats.toString(),
                        LocalVideoStreamStats::class.java
                    )
                videoStreamStats?.let {
                    showBottomDialog(it)
                }
            }
    }

    private fun parseRemoteAudioStats(stats: RTCStatsReport) {
        stats.statsMap.values.filter { it.type == "inbound-rtp" }
            .findLast { it.toString().contains("mediaType: \"audio\"") }
            ?.let { rtcStats ->
                val audioStreamStats =
                    gson.fromJson(
                        rtcStats.toString(),
                        RemoteAudioStreamStats::class.java
                    )
                audioStreamStats?.let {
                    showBottomDialog(it)
                }
            }
    }

    private fun parseRemoteVideoStats(stats: RTCStatsReport) {
        stats.statsMap.values.filter { it.type == "inbound-rtp" }
            .findLast { it.toString().contains("mediaType: \"video\"") }
            ?.let { rtcVideoStats ->
                val videoStreamStats =
                    gson.fromJson(
                        rtcVideoStats.toString(),
                        RemoteVideoStreamStats::class.java
                    )
                videoStreamStats?.let {
                    showBottomDialog(it)
                }
            }
    }

    private fun showBottomDialog(streamStats: StreamStats) {
        activity?.runOnUiThread {
            if (mStatsDialog == null) {
                mStatsDialog = BottomSheetDialog(requireContext())
                mStatsDialog?.setOnDismissListener {
                    mStatsjob?.cancel()
                }
            }
            updateDialogInfo(streamStats)
            if (mStatsDialog?.isShowing == false) mStatsDialog?.show()
        }
    }

    private fun updateDialogInfo(streamStats: StreamStats) {
        val statDialogView = layoutInflater.inflate(R.layout.stats_info_dialog, null)
        val dialogTitleTextView = statDialogView.findViewById<TextView>(R.id.stats_dialog_title)
        val dialogParticipantIdTextView =
            statDialogView.findViewById<TextView>(R.id.stats_participant_id)
        val dialogStatInfoTextView = statDialogView.findViewById<TextView>(R.id.stats_info)
        val dialogButtonToggleStats = statDialogView.findViewById<TextView>(R.id.toggleStatsButton)

        dialogParticipantIdTextView.text = "ParticipantId: ${mStatsParticipant?.participantId}"

        val stringBuilder = StringBuilder()
        when (streamStats) {
            is RemoteVideoStreamStats -> {
                dialogTitleTextView.text = getString(R.string.video_stats_title)
                stringBuilder.append("frameWidth: ${streamStats.frameWidth}\n")
                stringBuilder.append("frameHeight: ${streamStats.frameHeight}\n")
                stringBuilder.append("bytesReceived: ${streamStats.bytesReceived}\n")
                stringBuilder.append("packetsReceived: ${streamStats.packetsReceived}\n")
                stringBuilder.append("packetsLost: ${streamStats.packetsLost}\n")
                stringBuilder.append("framesPerSecond: ${streamStats.framesPerSecond}\n")
                stringBuilder.append("totalInterFrameDelay: ${streamStats.totalInterFrameDelay}\n")
                dialogButtonToggleStats.text = getString(R.string.audio)
                dialogButtonToggleStats.setOnClickListener {
                    mStatsParticipant?.let {
                        startStatsJob(
                            it.participantId,
                            SELF_STREAM_KEY,
                            StatsSource.REMOTE_AUDIO
                        )
                    }
                }
            }
            is LocalVideoStreamStats -> {
                dialogTitleTextView.text = getString(R.string.video_stats_title)
                stringBuilder.append("frameWidth: ${streamStats.frameWidth}\n")
                stringBuilder.append("frameHeight: ${streamStats.frameHeight}\n")
                stringBuilder.append("packetsSent: ${streamStats.packetsSent}\n")
                stringBuilder.append("bytesSent: ${streamStats.bytesSent}\n")
                stringBuilder.append("headerBytesSent: ${streamStats.headerBytesSent}\n")
                stringBuilder.append("nackCount: ${streamStats.nackCount}\n")
                dialogButtonToggleStats.text = getString(R.string.audio)
                dialogButtonToggleStats.setOnClickListener {
                    mStatsParticipant?.let {
                        startStatsJob(
                            it.participantId,
                            SELF_STREAM_KEY,
                            StatsSource.LOCAL_AUDIO
                        )
                    }
                }
            }
            is RemoteAudioStreamStats -> {
                dialogTitleTextView.text = getString(R.string.audio_stats_title)
                stringBuilder.append("jitter: ${streamStats.jitter}\n")
                stringBuilder.append("packetsLost: ${streamStats.packetsLost}\n")
                stringBuilder.append("packetsReceived: ${streamStats.packetsReceived}\n")
                stringBuilder.append("bytesReceived: ${streamStats.bytesReceived}\n")
                stringBuilder.append("audioLevel: ${streamStats.audioLevel}\n")
                stringBuilder.append("totalAudioEnergy: ${streamStats.totalAudioEnergy}\n")
                stringBuilder.append("totalSamplesDuration: ${streamStats.totalSamplesDuration}\n")
                dialogButtonToggleStats.text = getString(R.string.video)
                dialogButtonToggleStats.setOnClickListener {
                    mStatsParticipant?.let {
                        startStatsJob(
                            it.participantId,
                            SELF_STREAM_KEY,
                            StatsSource.REMOTE_VIDEO
                        )
                    }
                }
            }
            is LocalAudioStreamStats -> {
                dialogTitleTextView.text = getString(R.string.audio_stats_title)
                stringBuilder.append("packetsSent: ${streamStats.packetsSent}\n")
                stringBuilder.append("bytesSent: ${streamStats.bytesSent}\n")
                stringBuilder.append("retransmittedPacketsSent: ${streamStats.retransmittedPacketsSent}\n")
                stringBuilder.append("retransmittedBytesSent: ${streamStats.retransmittedBytesSent}\n")
                stringBuilder.append("headerBytesSent: ${streamStats.headerBytesSent}\n")
                dialogButtonToggleStats.text = getString(R.string.video)
                dialogButtonToggleStats.setOnClickListener {
                    mStatsParticipant?.let {
                        startStatsJob(
                            it.participantId,
                            SELF_STREAM_KEY,
                            StatsSource.LOCAL_VIDEO
                        )
                    }
                }
            }
        }
        dialogStatInfoTextView.text = stringBuilder.toString()
        mStatsDialog?.setContentView(statDialogView)
    }

    private fun setSharingParticipant(sharingParticipant: Participant) {
        mSharingParticipant = sharingParticipant
        roomsViewModel.subscribe(
            sharingParticipant.participantId, "presentation",
            StreamConfig(audioEnabled = false, videoEnabled = true)
        )
    }

    override fun onItemClicked(model: Participant) {
        speakerViewEnabled = false
        mStatsParticipant = StatsParticipant(
            participantId = model.participantId,
            isSelf = model.isSelf,
            isPresentation = false
        )

        if (model.streams.find { it.streamKey == SELF_STREAM_KEY }?.videoEnabled != StreamStatus.ENABLED) {
            if (model.isSelf) {
                // Gel Local Audio Stats
                startStatsJob(
                    model.participantId,
                    SELF_STREAM_KEY,
                    StatsSource.LOCAL_AUDIO
                )
            } else {
                // Get Remote Audio Stats
                startStatsJob(
                    model.participantId,
                    SELF_STREAM_KEY,
                    StatsSource.LOCAL_AUDIO
                )
            }
        } else {
            if (model.isSelf) {
                // Get Local Video Stats
                startStatsJob(
                    model.participantId,
                    SELF_STREAM_KEY,
                    StatsSource.LOCAL_VIDEO
                )
            } else {
                // Get Remote Video Stats
                startStatsJob(
                    model.participantId,
                    SELF_STREAM_KEY,
                    StatsSource.REMOTE_VIDEO
                )
            }
        }
    }

    override fun notifyTileSelfSurfaceId(participantSurface: SurfaceViewRenderer) {
        selfSurface = participantSurface
    }

    /**
     * Notifies TelnyxClient a surface was created and is ready to be initiated.
     */
    override fun notifyTileSurfaceId(
        participantSurface: SurfaceViewRenderer,
        participantId: String,
        streamKey: String
    ) {
        Timber.tag("RoomFragment").d("notifySurfaceId: $participantSurface $participantId")
        roomsViewModel.setParticipantSurface(participantId, participantSurface, streamKey)
    }

    override fun unsubscribeTileToStream(participantId: String, streamKey: String) {
        roomsViewModel.unsubscribe(participantId, streamKey)
    }

    override fun subscribeTileToStream(participantId: String, streamKey: String) {
        roomsViewModel.subscribe(
            participantId, streamKey,
            StreamConfig(audioEnabled = true, videoEnabled = true)
        )
    }

    override fun notifyExtraParticipants(extraParticipants: Int) {
        if (extraParticipants > 0) {
            "$extraParticipants more".also { fabExtraParticipant.text = it }
            fabExtraParticipant.visibility = View.VISIBLE
        } else {
            fabExtraParticipant.visibility = View.GONE
        }
    }

    override fun onStop() {
        super.onStop()
        if (toggledAudio) {
            roomsViewModel.setMicState(MediaOnStart.ENABLED)
        }
        if (toggledVideo) {
            roomsViewModel.setCameraState(MediaOnStart.ENABLED)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mSharingParticipant?.let {
            it.streams.find { it.streamKey == "presentation" }?.videoTrack?.removeSink(mainSurface)
            mainSurface.release()
        }
        participantTileRecycler.adapter = null
    }
}

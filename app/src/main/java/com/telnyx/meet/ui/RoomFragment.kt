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
import com.telnyx.video.sdk.webSocket.model.receive.PluginDataBody
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamConfig
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import com.telnyx.video.sdk.utilities.CameraDirection
import com.telnyx.video.sdk.utilities.PublishConfigHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.room_fragment.*
import kotlinx.coroutines.*
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
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
        private const val MEDIA_STREAM_HELPER_KEY = "003"

        private enum class ViewMode {
            FULL_PARTICIPANTS,
            MAIN_SHARE_VIEW_AND_PARTICIPANTS,
            MAIN_SHARE_VIEW_ONLY
        }

        private enum class CapturerConstraints(val value: Int) {
            // 720p at 30 fps
            WIDTH(1280),
            HEIGHT(720),
            FPS(30)
        }
    }

    private var menu: Menu? = null
    private var selfSurface: SurfaceViewRenderer? = null
    private var mStatsDialog: BottomSheetDialog? = null
    private var mStatsParticipant: StatsParticipant? = null

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

    private var toggledVideo = false
    private var toggledAudio = false
    private var speakerViewEnabled = false
    private var isFullShare = false

    private var mBound: Boolean = false
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private lateinit var publishConfigHelper: PublishConfigHelper

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
        mRefreshTokenJob = CoroutineScope(Dispatchers.Main).launch {
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
                    item.icon = context?.getDrawable(R.drawable.ic_camera_off_white)
                    startCameraCapture()
                    true
                } else {
                    item.icon = context?.getDrawable(R.drawable.ic_camera_white)
                    stopCameraCapture()
                    false
                }
                true
            }

            R.id.action_mic -> {
                toggledAudio = if (!toggledAudio) {
                    item.icon = context?.getDrawable(R.drawable.ic_mic_off_white)
                    startAudioCapture()
                    true
                } else {
                    item.icon = context?.getDrawable(R.drawable.ic_mic_white)
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
                publishConfigHelper.changeCameraDirection()
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

            R.id.action_report_issue -> {
                showIssueDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stopAudioCapture() {
        roomsViewModel.unpublish(AUDIO_TRACK_KEY)
    }

    private fun startAudioCapture() {
        publishConfigHelper =
            PublishConfigHelper(
                requireContext(),
                CameraDirection.FRONT,
                MEDIA_STREAM_HELPER_KEY
            )
        publishConfigHelper.createAudioTrack(true, AUDIO_TRACK_KEY)
        roomsViewModel.publish(publishConfigHelper)
    }

    private fun stopCameraCapture() {
        publishConfigHelper.stopCapture()
        selfSurface?.let { publishConfigHelper.releaseSurfaceView(it) }
        roomsViewModel.unpublish(VIDEO_TRACK_KEY)
    }

    private fun startCameraCapture() {
        publishConfigHelper =
            PublishConfigHelper(
                requireContext(),
                CameraDirection.FRONT,
                MEDIA_STREAM_HELPER_KEY
            )
        selfSurface?.let { publishConfigHelper.setSurfaceView(it) }
        publishConfigHelper.createVideoTrack(
            CapturerConstraints.WIDTH.value,
            CapturerConstraints.HEIGHT.value,
            CapturerConstraints.FPS.value, true, VIDEO_TRACK_KEY
        )
        roomsViewModel.publish(publishConfigHelper)
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
                startRemoteVideoStatsJob(participantId, true)
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

    private fun checkInitialMediaStatus() {
        CoroutineScope(Dispatchers.Main).launch {
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
            }
        }
    }

    private fun setupRecyclerView() {
        setRecyclerLayoutParams(currentViewMode)
        participantTileRecycler.adapter = participantAdapter
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
        }

        roomsViewModel.getParticipants().observe(viewLifecycleOwner) { participants ->
            participants.let { participantList ->
                participantAdapter.setData(participantList)
                if (participantList.size > 0) {
                    selfParticipantId = participantList[0].participantId
                    selfParticipantHandleId = participantList[0].id
                }
            }
        }

        roomsViewModel.getJoinedParticipant().observe(viewLifecycleOwner) { participantJoined ->
            participantJoined?.let { joinedParticipantEvent ->
                joinedParticipantEvent.getContentIfNotHandled()?.let {
                    participantAdapter.addParticipant(it)
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
            .observe(viewLifecycleOwner) { participantChangedStreams ->
                Timber.tag("RoomFragment")
                    .d("Participant $participantChangedStreams stream changed")
                participantAdapter.notifyDataSetChanged()
            }

        if (roomsViewModel.shouldAcceptFirstSharingInfo) {
            setSharingPeekObserver()
        } else {
            setSharingEventObserver()
        }

        roomsViewModel.getSpeakingParticipant().observe(viewLifecycleOwner) { speakingParticipant ->
            speakingParticipant?.let {
                participantAdapter.notifyDataSetChanged()
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

    private fun setSharingEventObserver() {
        roomsViewModel.getParticipantSharingChanged()
            .observe(viewLifecycleOwner) { participantSharingEvent ->
                participantSharingEvent.getContentIfNotHandled()?.let { participantSharing ->
                    processParticipantSharing(participantSharing)
                }
            }
    }

    private fun setSharingPeekObserver() {
        roomsViewModel.getParticipantSharingChanged()
            .observe(viewLifecycleOwner) { participantSharingEvent ->
                participantSharingEvent.peekContent().let { participantSharing ->
                    processParticipantSharing(participantSharing)
                    removeSharingPeekObserver()
                    setSharingEventObserver()
                }
            }
    }

    private fun removeSharingPeekObserver() {
        roomsViewModel.getParticipantSharingChanged().removeObservers(viewLifecycleOwner)
    }

    private fun processParticipantSharing(participantSharing: Participant) {
        when (participantSharing.sharingEnabled) {
            StreamStatus.UNKNOWN -> {
                Timber.tag("RoomFragment")
                    .d("Participant $participantSharing not sharing")
                if (participantSharing.participantId == mSharingParticipant?.participantId) {
                    adaptUIToFullParticipants()
                    mSharingParticipant = null
                }
            }
            else -> {
                setSharingParticipant(participantSharing)
                if (currentViewMode == ViewMode.FULL_PARTICIPANTS) {
                    adaptUIToMainView()
                }
                participantSharing.sharingTrack?.addSink(mainSurface)
                participantSharing.sharingTrack?.setEnabled(true)
                roomsViewModel.setParticipantSurface(
                    participantSharing.participantId,
                    mainSurface,
                    true
                )
            }
        }
    }

    private fun startSelfVideoStatsJob() {
        mStatsjob?.cancel()
        roomsViewModel.getSelfVideoStatsObservable()
            .observe(viewLifecycleOwner) { videoStatsPair ->
                videoStatsPair.getContentIfNotHandled()?.let { videoStats ->
                    videoStats.statsMap.values.filter { it.type == "outbound-rtp" }
                        .findLast { it.toString().contains("mediaType: \"video\"") }
                        ?.let { rtcVideoStats ->
                            val videoStreamStats =
                                gson.fromJson(
                                    rtcVideoStats.toString(),
                                    SelfVideoStreamStats::class.java
                                )
                            videoStreamStats?.let {
                                Timber.tag("RoomFragment")
                                    .d("SelfParticipant video STATS: $it")
                                showBottomDialog(it)
                            }
                        }
                }
            }
        mStatsjob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                roomsViewModel.getSelfVideoStats()
                delay(1500)
            }
        }
    }

    private fun startRemoteVideoStatsJob(participantId: String, isPresentation: Boolean) {
        mStatsjob?.cancel()
        roomsViewModel.getRemoteVideoStatsObservable()
            .observe(viewLifecycleOwner) { videoStatsPair ->
                videoStatsPair.getContentIfNotHandled()?.let { (participantId, videoStats) ->
                    videoStats.statsMap.values.filter { it.type == "inbound-rtp" }
                        .findLast { it.toString().contains("mediaType: \"video\"") }
                        ?.let { rtcVideoStats ->
                            val videoStreamStats =
                                gson.fromJson(
                                    rtcVideoStats.toString(),
                                    RemoteVideoStreamStats::class.java
                                )
                            videoStreamStats?.let {
                                Timber.tag("RoomFragment")
                                    .d("ParticipantID: $participantId video STATS: $it")
                                showBottomDialog(it)
                            }
                        }
                }
            }
        mStatsjob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                roomsViewModel.getRemoteVideoStats(participantId, isPresentation)
                delay(1500)
            }
        }
    }

    private fun startAudioStatsJob() {
        mStatsjob?.cancel()
        roomsViewModel.getAudioBridgeStatsObservable()
            .observe(viewLifecycleOwner) { videoStatsPair ->
                videoStatsPair.getContentIfNotHandled()?.let { audioStats ->
                    if (mStatsParticipant?.isSelf == true) {
                        audioStats.statsMap.values.filter { it.type == "outbound-rtp" }
                            .findLast { it.toString().contains("mediaType: \"audio\"") }
                            ?.let { rtcStats ->
                                val audioStreamStats =
                                    gson.fromJson(
                                        rtcStats.toString(),
                                        AudioBridgeOutputStreamStats::class.java
                                    )
                                audioStreamStats?.let {
                                    showBottomDialog(it)
                                }
                            }
                    } else {
                        audioStats.statsMap.values.filter { it.type == "inbound-rtp" }
                            .findLast { it.toString().contains("mediaType: \"audio\"") }
                            ?.let { rtcStats ->
                                val audioStreamStats =
                                    gson.fromJson(
                                        rtcStats.toString(),
                                        AudioBridgeInputStreamStats::class.java
                                    )
                                audioStreamStats?.let {
                                    showBottomDialog(it)
                                }
                            }
                    }
                }
            }
        mStatsjob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                roomsViewModel.getAudioBridgeStats()
                delay(5000)
            }
        }
    }

    private fun showBottomDialog(streamStats: StreamStats) {
        if (mStatsDialog == null) {
            mStatsDialog = BottomSheetDialog(requireContext())
            mStatsDialog?.setOnDismissListener {
                mStatsjob?.cancel()
                roomsViewModel.getRemoteVideoStatsObservable().removeObservers(viewLifecycleOwner)
                roomsViewModel.getSelfVideoStatsObservable().removeObservers(viewLifecycleOwner)
                roomsViewModel.getAudioBridgeStatsObservable().removeObservers(viewLifecycleOwner)
            }
        }
        updateDialogInfo(streamStats)
        if (mStatsDialog?.isShowing == false) mStatsDialog?.show()
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
                    startAudioStatsJob()
                }
            }
            is SelfVideoStreamStats -> {
                dialogTitleTextView.text = getString(R.string.video_stats_title)
                stringBuilder.append("frameWidth: ${streamStats.frameWidth}\n")
                stringBuilder.append("frameHeight: ${streamStats.frameHeight}\n")
                stringBuilder.append("packetsSent: ${streamStats.packetsSent}\n")
                stringBuilder.append("bytesSent: ${streamStats.bytesSent}\n")
                stringBuilder.append("headerBytesSent: ${streamStats.headerBytesSent}\n")
                stringBuilder.append("nackCount: ${streamStats.nackCount}\n")
                dialogButtonToggleStats.text = getString(R.string.audio)
                dialogButtonToggleStats.setOnClickListener {
                    startAudioStatsJob()
                }
            }
            is AudioBridgeInputStreamStats -> {
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
                        startRemoteVideoStatsJob(it.participantId, it.isPresentation)
                    }
                }
            }
            is AudioBridgeOutputStreamStats -> {
                dialogTitleTextView.text = getString(R.string.audio_stats_title)
                stringBuilder.append("packetsSent: ${streamStats.packetsSent}\n")
                stringBuilder.append("bytesSent: ${streamStats.bytesSent}\n")
                stringBuilder.append("retransmittedPacketsSent: ${streamStats.retransmittedPacketsSent}\n")
                stringBuilder.append("retransmittedBytesSent: ${streamStats.retransmittedBytesSent}\n")
                stringBuilder.append("headerBytesSent: ${streamStats.headerBytesSent}\n")
                dialogButtonToggleStats.text = getString(R.string.video)
                dialogButtonToggleStats.setOnClickListener {
                    startSelfVideoStatsJob()
                }
            }
        }
        dialogStatInfoTextView.text = stringBuilder.toString()
        mStatsDialog?.setContentView(statDialogView)
    }

    private fun setSharingParticipant(sharingParticipant: Participant) {
        mSharingParticipant = sharingParticipant
        roomsViewModel.subscribe(
            sharingParticipant.participantId,
            true, "SharingSubscription",
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
        if (model.videoEnabled != StreamStatus.ENABLED) {
            startAudioStatsJob()
        } else {
            if (model.isSelf) {
                startSelfVideoStatsJob()
            } else {
                startRemoteVideoStatsJob(model.participantId, false)
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
        participantId: String
    ) {
        Timber.tag("RoomFragment").d("notifySurfaceId: $participantSurface $participantId")
        roomsViewModel.setParticipantSurface(participantId, participantSurface, false)
    }

    override fun unsubscribeTileToStream(participantId: String) {
        roomsViewModel.unsubscribe(participantId, false, "VideoSubscription")
    }

    override fun subscribeTileToStream(participantId: String) {
        roomsViewModel.subscribe(
            participantId, false, "VideoSubscription",
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

    override fun onDestroyView() {
        super.onDestroyView()
        participantTileRecycler.adapter = null
    }
}

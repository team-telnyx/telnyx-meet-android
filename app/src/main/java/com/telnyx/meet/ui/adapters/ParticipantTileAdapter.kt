package com.telnyx.meet.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.telnyx.meet.R
import com.telnyx.meet.databinding.ParticipantTileItemBinding
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import timber.log.Timber

interface ParticipantTileListener {
    fun onItemClicked(model: Participant)
    fun notifyTileSelfSurfaceId(participantSurface: SurfaceViewRenderer)
    fun notifyTileSurfaceId(
        participantSurface: SurfaceViewRenderer,
        participantId: String,
        streamKey: String
    )

    fun unsubscribeTileToStream(participantId: String, streamKey: String)
    fun subscribeTileToStream(participantId: String, streamKey: String)
    fun notifyExtraParticipants(extraParticipants: Int)
}

class ParticipantTileAdapter(private val participantTileListener: ParticipantTileListener) :
    RecyclerView.Adapter<ParticipantTileAdapter.ParticipantTileHolder>() {

    private val participants = mutableListOf<Participant>()
    private val participantsInAdapter = mutableListOf<Participant>()
    private val viewHolderMap: MutableMap<ParticipantTileHolder, VideoTrack?> = HashMap()
    private var notifyDatasetJob: Job? = null

    private var maxParticipants: Int = 8

    override fun onViewDetachedFromWindow(holder: ParticipantTileHolder) {
        super.onViewDetachedFromWindow(holder)
        viewHolderMap[holder]?.let {
            Timber.tag("ParticipantTileAdapter").d(
                "detach remove surface: video: $it surface ${holder.binding.participantTileSurface}"
            )
            it.removeSink(holder.binding.participantTileSurface)
            holder.binding.participantTileSurface.release()
            viewHolderMap.remove(holder)
        }
    }

    fun setData(data: List<Participant>) {
        participants.clear()
        participants.addAll(data)
        calculateParticipants()
    }

    private fun calculateParticipants() {
        participantsInAdapter.clear()
        participantsInAdapter.addAll(participants.take(maxParticipants))
        val remainingParticipants = participants.size - participantsInAdapter.size
        if (remainingParticipants > 0) {
            pauseRemainingParticipants(participants.takeLast(remainingParticipants))
        }
        participantTileListener.notifyExtraParticipants(remainingParticipants)
        initNotifyDataSetJob()
    }

    fun initNotifyDataSetJob() {
        notifyDatasetJob?.cancel()
        val handler = CoroutineExceptionHandler { _, exception ->
            Timber.tag("ParticipantTileAdapter")
                .d("CoroutineExceptionHandler tile adapter update: $exception")
        }
        notifyDatasetJob = CoroutineScope(Main).launch(handler) {
            if (isActive) {
                delay(1000)
                notifyDataSetChanged()
            }
        }
    }

    private fun pauseRemainingParticipants(participantsNotVisible: List<Participant>) {
        participantsNotVisible.forEach {
            participantTileListener.unsubscribeTileToStream(it.participantId, "self")
        }
    }

    fun getPositionFromParticipant(participant: Participant): Int {
        return participantsInAdapter.indexOf(participant)
    }

    fun addParticipant(participant: Participant) {
        var modified = false
        participants.find { it.participantId == participant.participantId }?.let {
            it.id = participant.id
            it.externalUsername = participant.externalUsername
            it.streams = participant.streams
            it.audioBridgeId = participant.audioBridgeId
            it.canReceiveMessages = participant.canReceiveMessages
        } ?: run {
            Timber.tag("ParticipantTileAdapter")
                .d("Adding participant: ${participant.participantId}")
            participants.add(participant)
            modified = true
        }
        if (modified) calculateParticipants()
    }

    fun removeParticipant(leavingId: Long) {
        var modified = false
        participants.find { it.id == leavingId }?.let {
            Timber.tag("ParticipantTileAdapter")
                .d("Removing participant: $leavingId ${it.participantId}")
            participants.remove(it)
            modified = true
        }
        if (modified) calculateParticipants()
    }

    /*fun adjustSpeakingParticipants(list: List<Participant>) {
        Timber.tag("ParticipantTileAdapter").d("Updating participants speaking: $list")
        notifyDataSetChanged()
    }*/

    override fun getItemId(position: Int): Long {
        return participantsInAdapter[position].id
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ParticipantTileHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ParticipantTileHolder(
            inflater.inflate(
                R.layout.participant_tile_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = participantsInAdapter.size

    override fun onBindViewHolder(holder: ParticipantTileHolder, position: Int) {
        holder.bind(participantsInAdapter[position], participantTileListener, viewHolderMap)
    }

    class ParticipantTileHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val holder = this
        val binding = ParticipantTileItemBinding.bind(itemView)
        fun bind(
            model: Participant,
            participantTileListener: ParticipantTileListener,
            viewHolderMap: MutableMap<ParticipantTileHolder, VideoTrack?>
        ) {
            binding.apply {
                Timber.tag("ParticipantTileAdapter").d("onBind() isSelf: ${model.isSelf}")
                participantTileId.text =
                    model.participantId.substring(0, 5)
                participantTileName.text = model.externalUsername.toString()

                Timber.tag("ParticipantTileAdapter")
                    .d("onBind() talkingUpdate ${model.externalUsername} ${model.isTalking}")

                if (model.isAudioCensored == true) {
                    isSpeakingIconId.setImageResource(R.drawable.ic_speaker_attendee_off)
                    isSpeakingIconId.visibility = View.VISIBLE
                } else {
                    isSpeakingIconId.setImageResource(R.drawable.ic_campaign_white_dp)
                    if (model.isTalking == "talking") {
                        isSpeakingIconId.visibility = View.VISIBLE
                    } else {
                        isSpeakingIconId.visibility = View.INVISIBLE
                    }
                }
                model.connectionQualityLevel?.let {
                    networkQualityIcon.visibility = View.VISIBLE
                    networkQualityIcon.text = it.icon
                } ?: run {
                    networkQualityIcon.visibility = View.GONE
                }
            }


            if (model.isSelf) participantTileListener.notifyTileSelfSurfaceId(binding.participantTileSurface)

            // TODO review this logic: will only subscribe to audio, if video is enabled as well
            when (model.streams.find { it.streamKey == "self" }?.videoEnabled) {
                StreamStatus.ENABLED -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() STARTED")
                    participantTileListener.subscribeTileToStream(model.participantId, "self")
                    binding.participantTileSurface.visibility = View.VISIBLE
                    binding.participantTilePlaceHolder.visibility = View.GONE
                    participantTileListener.notifyTileSurfaceId(
                        binding.participantTileSurface,
                        model.participantId,
                        "self"
                    )
                    model.streams.find { it.streamKey == "self" }?.videoTrack?.let {
                        if (viewHolderMap[holder] != it) {
                            // Updates only if previous register differs from what we need
                            viewHolderMap[holder]?.removeSink(holder.binding.participantTileSurface)
                            holder.binding.participantTileSurface.release()
                            viewHolderMap[holder] = it
                            it.addSink(binding.participantTileSurface)
                            it.setEnabled(true)
                        }
                    }
                }
                else -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() PAUSED")
                    binding.participantTileSurface.visibility = View.GONE
                    binding.participantTilePlaceHolder.visibility = View.VISIBLE
                    participantTileListener.unsubscribeTileToStream(model.participantId, "self")
                }
            }
/*
            when (model.streams.find { it.streamKey == "self" }?.audioEnabled) {
            }
                StreamStatus.ENABLED -> {
                    participantTileListener.subscribeTileToStream(model.participantId)
                }
                else -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() Do nothing")
                }
*/
            itemView.setOnClickListener {
                participantTileListener.onItemClicked(model)
            }
        }
    }

    fun setMaxParticipants(maxParticipants: Int) {
        this.maxParticipants = maxParticipants
        calculateParticipants()
    }
}

package com.telnyx.meet.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.telnyx.meet.R
import com.telnyx.meet.ui.models.MessageUI
import com.telnyx.meet.ui.utilities.getTimeHHmm
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import kotlinx.android.synthetic.main.admin_message_item.view.*
import kotlinx.android.synthetic.main.remote_message_item.view.*
import kotlinx.android.synthetic.main.self_message_item.view.*

class MessageListAdapter(
    context: Context,
    messageList: MutableList<MessageUI>?
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val SELF_MESSAGE = 10
        private const val REMOTE_MESSAGE = 20
        private const val ADMIN_MESSAGE = 30
    }

    private val textRoomParticipantList = mutableListOf<Participant>()
    private val mContext: Context = context
    private val mMessageList = mutableListOf<MessageUI>()

    init {
        if (messageList != null) {
            mMessageList.addAll(messageList)
        }
    }

    fun setData(data: List<MessageUI>) {
        mMessageList.clear()
        mMessageList.addAll(data)
        notifyDataSetChanged()
    }

    fun getMessages(): MutableList<MessageUI> = mMessageList

    fun addMessageToChat(textRoomMessageReceived: MessageUI) {
        mMessageList.add(textRoomMessageReceived)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (mMessageList[position].sender) {
            "SELF" -> {
                SELF_MESSAGE
            }
            "ADMIN" -> {
                ADMIN_MESSAGE
            }
            else -> {
                REMOTE_MESSAGE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == SELF_MESSAGE) {
            val inflater = LayoutInflater.from(mContext)
            return SelfMessageHolder(
                inflater.inflate(
                    R.layout.self_message_item,
                    parent,
                    false
                )
            )
        } else if (viewType == REMOTE_MESSAGE) {
            val inflater = LayoutInflater.from(mContext)
            return RemoteMessageHolder(
                inflater.inflate(
                    R.layout.remote_message_item,
                    parent,
                    false
                )
            )
        } else {
            val inflater = LayoutInflater.from(mContext)
            return AdminMessageHolder(
                inflater.inflate(
                    R.layout.admin_message_item,
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = mMessageList[position]
        val sender =
            textRoomParticipantList.find { it.participantId == message.sender }?.externalUsername
        val recipientNames = message.fullMessage.recipients.map { recipientId ->
            textRoomParticipantList.find { it.participantId == recipientId }?.externalUsername
        }
        when (holder.itemViewType) {
            SELF_MESSAGE -> {
                (holder as SelfMessageHolder).bind(message, recipientNames)
            }
            REMOTE_MESSAGE -> {
                (holder as RemoteMessageHolder).bind(message, recipientNames, sender)
            }
            else -> {
                (holder as AdminMessageHolder).bind(message)
            }
        }
    }

    override fun getItemCount(): Int {
        return mMessageList.size
    }

    fun addParticipant(participant: Participant) {
        textRoomParticipantList.add(participant)
    }

    class SelfMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model: MessageUI, recepientNames: List<String?>) {

            val isPrivateMessage = !recepientNames.isNullOrEmpty()
            val text = model.fullMessage.message.payload
            val date = model.date

            if (isPrivateMessage) {
                itemView.sendto_chat_self.visibility = View.VISIBLE
                itemView.sendto_chat_self.text = "Private to $recepientNames"
            } else {
                itemView.sendto_chat_self.visibility = View.GONE
            }
            itemView.text_chat_self.text = text
            itemView.date_chat_self.text = getTimeHHmm(date)
        }
    }

    class RemoteMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model: MessageUI, recepientNames: List<String?>, sender: String?) {

            val isPrivateMessage = !recepientNames.isNullOrEmpty()
            val text = model.fullMessage.message.payload
            val date = model.date
            val senderText = sender ?: model.sender

            if (isPrivateMessage) {
                itemView.sendfrom_chat_remote.text =
                    "Private from $senderText to $recepientNames"
            } else {
                itemView.sendfrom_chat_remote.text = senderText
            }
            itemView.text_chat_remote.text = text
            itemView.date_chat_remote.text = getTimeHHmm(date)
        }
    }

    class AdminMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model: MessageUI) {
            val text = model.fullMessage.message.payload
            itemView.text_chat_admin.text = text
            itemView.date_chat_admin.text = getTimeHHmm(model.date)
        }
    }
}

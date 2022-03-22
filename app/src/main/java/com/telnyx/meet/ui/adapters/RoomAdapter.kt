package com.telnyx.meet.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.telnyx.meet.R
import com.telnyx.meet.data.model.RoomDetails
import kotlinx.android.synthetic.main.room_item.view.*

interface BindableAdapter<T> {
    fun setData(data: T)
    fun addData(data: T)
}

interface ClickListener {
    fun onItemClicked(roomId: String, roomName: String)
    fun snackBarDismissed(roomId: String, roomName: String)
}

class RoomAdapter(private val clickListener: ClickListener) :
    RecyclerView.Adapter<RoomAdapter.RoomHolder>(),
    BindableAdapter<List<RoomDetails>> {

    private var removedPosition: Int = 0
    private var removedItem: RoomDetails? = null

    private val rooms = mutableListOf<RoomDetails>()

    override fun setData(data: List<RoomDetails>) {
        rooms.clear()
        rooms.addAll(data)
        notifyDataSetChanged()
    }

    override fun addData(data: List<RoomDetails>) {
        rooms.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RoomHolder {
        val inflater = LayoutInflater.from(parent.context)
        return RoomHolder(inflater.inflate(R.layout.room_item, parent, false))
    }

    override fun getItemCount(): Int = rooms.size

    override fun onBindViewHolder(holder: RoomHolder, position: Int) {
        holder.bind(rooms[position], clickListener)
    }

    fun removeItem(position: Int, viewHolder: RecyclerView.ViewHolder) {
        removedItem = rooms[position]
        removedPosition = position

        rooms.removeAt(position)
        notifyItemRemoved(position)

        val snackbar =
            Snackbar.make(viewHolder.itemView, "$removedItem removed", Snackbar.LENGTH_LONG)
                .setAction("UNDO") {
                    removedItem?.let {
                        rooms.add(removedPosition, it)
                    }
                    notifyItemInserted(removedPosition)
                }
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(snackbar: Snackbar, event: Int) {
                when (event) {
                    DISMISS_EVENT_TIMEOUT -> {
                        removedItem?.let {
                            clickListener.snackBarDismissed(it.id, it.unique_name)
                        }
                    }
                }
            }
        }).show()
    }

    class RoomHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model: RoomDetails, clickListener: ClickListener) {
            if (model.unique_name == null) {
                model.unique_name = "Unknown name"
            }
            itemView.room_name.text = model.unique_name
            itemView.room_id.text = model.id
            itemView.setOnClickListener {
                clickListener.onItemClicked(model.id, model.unique_name)
            }
        }
    }
}

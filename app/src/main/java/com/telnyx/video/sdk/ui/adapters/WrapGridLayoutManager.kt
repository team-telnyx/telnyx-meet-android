package com.telnyx.video.sdk.ui.adapters

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

open class WrapGridLayoutManager : GridLayoutManager {

    constructor(ctx: Context, spanCount: Int) : super(ctx, spanCount)

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Timber.tag("WrapGridLayoutManager").e("IndexOutOfBound in RecyclerView")
        }
    }
}

package com.telnyx.meet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<VB:ViewBinding> : Fragment() {

    abstract val layoutId: Int

    private var _binding: VB? = null
    // This property is only valid between onCreateView and
// onDestroyView.
    val binding get() = _binding!!
    abstract fun inflate(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = inflate(inflater, container, savedInstanceState)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

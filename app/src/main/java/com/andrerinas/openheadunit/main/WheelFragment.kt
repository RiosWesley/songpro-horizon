package com.andrerinas.openheadunit.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andrerinas.openheadunit.R

/** Volante: the Horizon menu on a steering wheel key, and which keys do what while it is open. */
class WheelFragment : Fragment() {

    private var wheel: BydWheelSection? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_hz_wheel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val lastMessage = view.findViewById<TextView>(R.id.hz_wheel_log)
        wheel = BydWheelSection(view, viewLifecycleOwner.lifecycleScope) { line ->
            lastMessage.text = line
            lastMessage.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        wheel?.release()
        wheel = null
        super.onDestroyView()
    }
}

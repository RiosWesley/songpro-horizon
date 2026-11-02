package com.andrerinas.openheadunit.main

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.andrerinas.openheadunit.R

/**
 * Song Pro Horizon's sidebar: Início, Painel, Android Auto, Volante and Sobre, plus leaving the app.
 * Each section opens over Início, so Back always lands there; the sidebar hides on the screens
 * outside the five sections (the USB list, for instance).
 */
internal class HorizonSidebar(private val activity: Activity, private val nav: NavController) {

    private val sidebar: View = activity.findViewById(R.id.hz_sidebar)
    private val edge: View = activity.findViewById(R.id.hz_sidebar_edge)
    private val sections = linkedMapOf(
        R.id.hz_nav_home to R.id.homeFragment,
        R.id.hz_nav_cluster to R.id.bydPanelFragment,
        R.id.hz_nav_aa to R.id.androidAutoFragment,
        R.id.hz_nav_wheel to R.id.wheelFragment,
        R.id.hz_nav_about to R.id.horizonAboutFragment)
    // Screens opened from a section: the sidebar stays, with their section marked.
    private val subscreens = mapOf(R.id.mapTuneFragment to R.id.bydPanelFragment)

    init {
        for ((row, destination) in sections) activity.findViewById<View>(row).setOnClickListener { open(destination) }
        activity.findViewById<View>(R.id.hz_nav_exit).setOnClickListener { AppExit.exit(activity) }
        nav.addOnDestinationChangedListener { _, destination, _ -> show(destination.id) }
        keepAboveSystemBar()
    }

    /**
     * BYD's bar across the bottom of the screen stays over the app even in full screen, where the
     * root (SystemUI) does not make room for it: the sidebar and the screens end above it.
     */
    private fun keepAboveSystemBar() {
        val frame = sidebar.parent as View
        val root = frame.parent as View
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val insets = ViewCompat.getRootWindowInsets(frame) ?: return@addOnLayoutChangeListener
            val bar = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottom = (bar - root.paddingBottom).coerceAtLeast(0)
            if (frame.paddingBottom != bottom) frame.post { frame.setPadding(0, 0, 0, bottom) }
        }
    }

    fun open(destination: Int) {
        if (nav.currentDestination?.id == destination) return
        if (destination == R.id.homeFragment) {
            nav.popBackStack(R.id.homeFragment, false)
            return
        }
        nav.navigate(destination, null, NavOptions.Builder()
            .setPopUpTo(R.id.homeFragment, false)
            .setLaunchSingleTop(true)
            .build())
    }

    private fun show(shown: Int) {
        val destination = subscreens[shown] ?: shown
        val visibility = if (destination in sections.values) View.VISIBLE else View.GONE
        sidebar.visibility = visibility
        edge.visibility = visibility
        for ((row, section) in sections) activity.findViewById<View>(row).isActivated = section == destination
    }
}

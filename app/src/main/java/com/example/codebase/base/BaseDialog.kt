package com.example.codebase.base

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

/**
 * Base for simple dialogs with static content: a plain [AppCompatDialog] (not a DialogFragment) whose
 * UI is defined in XML and accessed through ViewBinding.
 *
 * - Data and callbacks are passed through the subclass constructor. The dialog is not restored after
 *   rotation or process death. When [context] is a LifecycleOwner (an Activity), the dialog dismisses
 *   itself if that host is destroyed while showing, so the window never leaks.
 * - Every dialog gets the same window: [dialogWidthRatio] of the screen width, height wrapping content,
 *   transparent background — so the layout root must draw its own background.
 * - The first `show()` runs `setContentView` → [initView] → [initListeners].
 *
 * Usage: `XxxDialog(this, message, onConfirm = viewModel::delete).show()`
 */
abstract class BaseDialog<VB : ViewBinding>(
    context: Context,
    bindingInflater: (LayoutInflater) -> VB,
    @StyleRes themeResId: Int = 0
) : AppCompatDialog(context, themeResId) {

    protected val binding: VB = bindingInflater(layoutInflater)

    abstract val isCancel: Boolean
    abstract val cancelTouchOutside: Boolean

    private val hostLifecycle: Lifecycle? = (context as? LifecycleOwner)?.lifecycle

    private val dismissOnHostDestroy = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCancelable(isCancel)
        setCanceledOnTouchOutside(cancelTouchOutside)

        initView()
        initListeners()
    }

    override fun onStart() {
        super.onStart()
        hostLifecycle?.addObserver(dismissOnHostDestroy)
    }

    override fun onStop() {
        super.onStop()
        hostLifecycle?.removeObserver(dismissOnHostDestroy)
    }

    protected abstract fun initView()

    protected open fun initListeners() {}
}

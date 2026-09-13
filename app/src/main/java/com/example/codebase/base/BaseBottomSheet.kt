package com.example.codebase.base

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Base for bottom sheets. By default the sheet opens fully expanded and skips the collapsed
 * state on dismiss ([expandOnShow]). Show it with `XxxBottomSheet().show(supportFragmentManager, XxxBottomSheet.TAG)`.
 */
abstract class BaseBottomSheet<VB : ViewBinding>(context: Context) :
    BottomSheetDialog(context) {
    private var _binding: VB? = null
    protected val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        _binding = inflate()
        setContentView(binding.root)
        super.onCreate(savedInstanceState)
        disableDragging()
        initViews()
        initActions()
        setOnDismissListener { onDismiss() }
    }

    abstract fun inflate(): VB

    open fun initViews() {}
    open fun initActions() {}
    open fun onDismiss() {}

    private fun disableDragging() {
        val bottomSheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.isDraggable = false
        }
    }

}

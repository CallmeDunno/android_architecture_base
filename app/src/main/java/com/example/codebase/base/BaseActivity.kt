package com.example.codebase.base

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Base for every screen Activity.
 *
 * `onCreate` runs a fixed sequence: edge-to-edge → inflate binding → system bar insets →
 * [initView] → [initListeners] → [observeData]. Subclasses implement the hooks instead of
 * overriding `onCreate`.
 */
abstract class BaseActivity<VB : ViewBinding>(
    private val bindingInflater: (LayoutInflater) -> VB
) : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!isActivityAvailable()) {
            finish()
            return
        }
        binding = bindingInflater(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        initView(savedInstanceState)
        initListeners()
        observeData()
    }

    /** Pads the root view by the system bars. Override for screens that draw behind them. */
    protected open fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    protected abstract fun initView(savedInstanceState: Bundle?)

    protected open fun initListeners() {}

    protected open fun observeData() {}

    /** Collects only while the Activity is at least STARTED; restarts on every return to STARTED. */
    protected fun <T> Flow<T>.collectWhenStarted(action: suspend (T) -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { action(it) }
            }
        }
    }

    open fun isActivityAvailable(): Boolean {
        return true
    }

    fun goTo(destination: Class<*>, key: String, singleValue: String? = null, bundle: Bundle? = null, canBack: Boolean = true) {
        val intent = Intent(this, destination)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (bundle != null) {
            intent.putExtra(key, bundle)
        }
        if (singleValue != null) {
            intent.putExtra(key, singleValue)
        }
        startActivity(intent)
        if (!canBack) finish()
    }

    fun clearAndGoTo(destination: Class<*>, singleValue: String? = null, key: String, canBack: Boolean = false) {
        val intent = Intent(this, destination)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (singleValue != null) {
            intent.putExtra(key, singleValue)
        }
        startActivity(intent)
        if (!canBack) finish()
    }

    fun goTo(destination: Class<*>, singleValue: String? = null, key: String, canBack: Boolean = true) {
        val intent = Intent(this, destination)
        if (singleValue != null) {
            intent.putExtra(key, singleValue)
        }
        startActivity(intent)
        if (!canBack) finish()
    }

    fun notify(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

}

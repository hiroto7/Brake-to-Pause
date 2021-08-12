package io.github.hiroto7.braketopause

import androidx.databinding.BindingAdapter
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

object BindingAdapters {
    @BindingAdapter("shown")
    @JvmStatic
    fun setShown(button: ExtendedFloatingActionButton, shown: Boolean) =
        button.run { if (shown) show() else hide() }
}
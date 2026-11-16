package com.example.mytestapplication.presentation

import androidx.annotation.StringRes
import com.example.mytestapplication.R
import com.example.mytestapplication.domain.Priority

@StringRes
fun Priority.labelResource(): Int = when (this) {
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}

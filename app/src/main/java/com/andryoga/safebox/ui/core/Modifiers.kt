package com.andryoga.safebox.ui.core

import androidx.compose.ui.Modifier

/**
 * Applies the 'ifTrue' modifier only if the [condition] is true.
 */
fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier
): Modifier {
    return if (condition) {
        this.then(ifTrue)
    } else {
        this
    }
}

/**
 * Applies the 'ifTrue' modifier only if [value] is not null.
 * The non-null 'value' is provided to the lambda.
 */
fun <T> Modifier.ifNotNull(
    value: T?,
    ifTrue: (value: T) -> Modifier
): Modifier {
    return if (value != null) {
        this.then(ifTrue(value))
    } else {
        this
    }
}
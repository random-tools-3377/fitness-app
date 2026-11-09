package io.github.randomtools3377.fitness.ui

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/**
 * Unlocks with the phone's own lock: fingerprint, face, or the phone's PIN or pattern. A phone
 * with no screen lock can't be asked, so there the lock simply opens, with a note saying why.
 */
@Composable
fun rememberUnlock(onUnlocked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        val activity = context.fragmentActivity()
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (activity == null || BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(context, "This phone has no screen lock, so the privacy lock can't ask for one.", Toast.LENGTH_LONG).show()
            onUnlocked()
        } else {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock")
                    .setSubtitle("Use your phone's lock")
                    .setAllowedAuthenticators(authenticators)
                    .build(),
            )
        }
    }
}

package com.example.movesmart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.data.remote.FirebaseAuthRepository
import com.example.movesmart.data.remote.MissingFirebaseAuthRepository
import com.example.movesmart.ui.auth.MemberAService
import com.google.firebase.FirebaseOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks packaged configuration and repository selection, not a remote sign-in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseConfigurationTest {
    @Test fun selectsAuthenticationRepositoryFromPackagedConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val options = FirebaseOptions.fromResource(context)
        val service = MemberAService.get(context)
        if (options == null) {
            assertFalse(service.isFirebaseReady)
            assertTrue(service.authRepository is MissingFirebaseAuthRepository)
        } else {
            assertFalse(options.applicationId.isBlank())
            assertFalse(options.apiKey.isBlank())
            assertFalse(options.projectId.isNullOrBlank())
            assertTrue(service.isFirebaseReady)
            assertTrue(service.authRepository is FirebaseAuthRepository)
        }
    }
}

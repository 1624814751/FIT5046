package com.example.movesmart.data.remote

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

interface GoogleLoginProvider {
    suspend fun getIdToken(activity: Activity): Result<String>
    suspend fun clear(): Result<Unit>
}

// Google login: Get account credentials
class GoogleCredentialLoginProvider(context: Context) : GoogleLoginProvider {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    override suspend fun getIdToken(activity: Activity): Result<String> {
        return try {
            val clientId = findClientId(activity)
            // This request comes from an explicit Google sign-in button.
            val option = GetSignInWithGoogleOption.Builder(clientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.failure(IllegalStateException("Google returned an unsupported credential."))
            } else {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                Result.success(token)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: GetCredentialCancellationException) {
            Result.failure(GoogleLoginCancelledException())
        } catch (error: NoCredentialException) {
            Result.failure(IllegalStateException("Google sign-in could not retrieve a credential. Check your connection and Google Play services, then try again.", error))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun clear(): Result<Unit> {
        return try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    @Suppress("DiscouragedApi")
    private fun findClientId(activity: Activity): String {
        val resourceId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        check(resourceId != 0) {
            "Google login is not configured. Add google-services.json with a Web client ID."
        }
        return activity.getString(resourceId)
    }
}

class GoogleLoginCancelledException : Exception("Google login was cancelled.")

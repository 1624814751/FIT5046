package com.example.movesmart.data.remote

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

interface GoogleLoginProvider {
    suspend fun getIdToken(activity: Activity): Result<String>
    suspend fun clear(): Result<Unit>
}

class GoogleCredentialLoginProvider(context: Context) : GoogleLoginProvider {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    override suspend fun getIdToken(activity: Activity): Result<String> {
        return try {
            val clientId = findClientId(activity)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
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
        } catch (error: GetCredentialCancellationException) {
            Result.failure(GoogleLoginCancelledException())
        } catch (error: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account is available on this device.", error))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun clear(): Result<Unit> {
        return runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
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

package com.example.movesmart.ui.auth

import android.content.Context
import com.example.movesmart.data.remote.FirebaseAuthRepository
import com.example.movesmart.data.remote.GoogleCredentialLoginProvider
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.data.remote.MissingFirebaseAuthRepository
import com.example.movesmart.shared.AuthRepository
import com.example.movesmart.shared.PreferencesRepository
import com.example.movesmart.ui.preferences.DataStorePreferencesRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MemberAService private constructor(context: Context) {
    val authRepository: AuthRepository
    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(context)
    val googleLoginProvider: GoogleLoginProvider = GoogleCredentialLoginProvider(context)
    val isFirebaseReady: Boolean

    init {
        val firebaseApp = FirebaseApp.initializeApp(context)
        isFirebaseReady = firebaseApp != null
        authRepository = if (firebaseApp == null) {
            MissingFirebaseAuthRepository()
        } else {
            FirebaseAuthRepository(FirebaseAuth.getInstance(firebaseApp))
        }
    }

    companion object {
        @Volatile
        private var instance: MemberAService? = null

        fun get(context: Context): MemberAService {
            return instance ?: synchronized(this) {
                instance ?: MemberAService(context.applicationContext).also { instance = it }
            }
        }
    }
}

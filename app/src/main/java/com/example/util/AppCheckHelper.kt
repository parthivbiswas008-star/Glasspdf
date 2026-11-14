package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_CACHE_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var cachedAppContext: Context? = null

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        cachedAppContext = context.applicationContext

        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val tokenCachePrefs = context.getSharedPreferences(
            "$TOKEN_CACHE_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            if (!debugToken.isNullOrEmpty()) {
                val previousSecret = debugPrefs.getString(DEBUG_SECRET_KEY, null)
                if (previousSecret != debugToken) {
                    tokenCachePrefs.edit().clear().commit()
                }
                // CRITICAL: MUST use commit() instead of apply() so the secret is written
                // synchronously before DebugAppCheckProviderFactory reads it.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")

            if (!debugToken.isNullOrEmpty()) {
                FirebaseAppCheck.getInstance().getAppCheckToken(true)
            }
        } else {
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            installRecaptchaProviderIfAvailable(context)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

fun installRecaptchaProviderIfAvailable(context: Context? = cachedAppContext): Boolean {
    val ctx = context ?: return false
    return try {
        val resId = ctx.resources.getIdentifier(
            RECAPTCHA_SITE_KEY_RES,
            "string",
            ctx.packageName
        )
        if (resId != 0) {
            val siteKey = ctx.getString(resId)
            if (siteKey.isNotBlank()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                ctx.getSharedPreferences(
                    "$TOKEN_CACHE_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                ).edit().clear().commit()

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                true
            } else {
                Log.e(TAG, "recaptcha_site_key resource string is blank.")
                false
            }
        } else {
            Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to install RecaptchaAppCheckProviderFactory", e)
        false
    }
}

package com.example.agentapp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Default BASE_URL. (It will be updated by SettingsActivity.)
    private var BASE_URL = "http://100.xx.xx.xx:5000/"

    // Create the Retrofit instance using the current BASE_URL.
    private var retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Instead of a lazy property, use a getter so that when the API is accessed,
    // it is always created from the current retrofit instance.
    val api: AgentApi
        get() = retrofit.create(AgentApi::class.java)

    // Call this method when the user saves new settings.
    fun setBaseUrl(newUrl: String) {
        BASE_URL = newUrl
        // Recreate the Retrofit instance using the new URL.
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

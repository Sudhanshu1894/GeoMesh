package com.example.resilentnet

import android.app.Application
import com.example.resilentnet.domain.MeshRepository

/**
 * Application entry point providing access to the MeshRepository singleton.
 */
class ResilientNetApp : Application() {
    lateinit var repository: MeshRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = MeshRepository(this)
    }

    companion object {
        lateinit var instance: ResilientNetApp
            private set
    }
}

package com.cinedrive.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt's singleton component is generated from here;
 * storage/scan/player modules are wired in Steps 2–5.
 */
@HiltAndroidApp
class CineDriveApp : Application()

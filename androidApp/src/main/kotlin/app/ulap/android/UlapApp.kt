package app.ulap.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The Application class. Hilt builds the app's dependency graph from here (D2). */
@HiltAndroidApp
class UlapApp : Application()

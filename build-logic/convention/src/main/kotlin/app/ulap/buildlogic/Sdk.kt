package app.ulap.buildlogic

/** SDK levels shared by every Android module (ulap-reference.md §2). */
internal object Sdk {
    const val COMPILE = 37
    const val MIN = 26

    /** The level Google Play currently requires; raise it yearly with the Play deadline. */
    const val TARGET = 36
}

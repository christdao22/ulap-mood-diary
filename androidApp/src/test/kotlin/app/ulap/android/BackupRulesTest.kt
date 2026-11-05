package app.ulap.android

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Nothing on this phone goes to Google's cloud backup or a device-to-device transfer
 * (technical-plan.md §6, README §14). The database and its passphrase only work on the phone
 * that made them, the journal is private, and the device ID must stay unique.
 *
 * On Android 12+ `allowBackup="false"` still allows device transfers, so the rule files matter
 * as much as the flag. These tests read the real merged manifest and resources.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: these tests read the manifest and resources only, not the Hilt graph.
@Config(application = Application::class)
class BackupRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `backup is turned off`() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `Android 12 and later exclude everything from cloud backup and device transfer`() {
        assertEquals(
            mapOf("cloud-backup" to EVERY_DOMAIN, "device-transfer" to EVERY_DOMAIN),
            excludedDomains(R.xml.data_extraction_rules),
        )
    }

    @Test
    fun `Android 8 to 11 exclude everything`() {
        assertEquals(mapOf("full-backup-content" to EVERY_DOMAIN), excludedDomains(R.xml.backup_rules))
    }

    /**
     * Each section of a rules file, with the domains it excludes whole (`path="."`). Any
     * `<include>` fails the test: nothing may be let back in.
     */
    private fun excludedDomains(xmlRes: Int): Map<String, Set<String>> {
        val sections = mutableMapOf<String, MutableSet<String>>()
        var section = ""
        for (tag in startTags(xmlRes)) {
            when (tag.name) {
                "include" -> error("$section includes ${tag.domain}")

                "exclude" -> if (tag.path == ".") sections.getValue(section) += tag.domain.orEmpty()

                "data-extraction-rules" -> Unit

                else -> {
                    section = tag.name
                    sections[section] = mutableSetOf()
                }
            }
        }
        return sections
    }

    private data class Tag(val name: String, val domain: String?, val path: String?)

    /** Every opening tag of a compiled XML resource, in order. */
    private fun startTags(xmlRes: Int): List<Tag> {
        val tags = mutableListOf<Tag>()
        val parser = context.resources.getXml(xmlRes)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    tags +=
                        Tag(
                            parser.name,
                            parser.getAttributeValue(null, "domain"),
                            parser.getAttributeValue(null, "path"),
                        )
                }
            }
        } finally {
            parser.close()
        }
        return tags
    }

    private companion object {
        val EVERY_DOMAIN = setOf("root", "file", "database", "sharedpref", "external")
    }
}

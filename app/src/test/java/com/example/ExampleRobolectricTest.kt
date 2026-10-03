package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Catatan: sdk dijalankan pada 35, bukan 36.
 *
 * Robolectric 4.16.1 menolak membuat sandbox SDK 36 di JDK 17 dengan pesan
 * "Android SDK 36 requires Java 21 (have Java 17)". Toolchain proyek ini dikunci
 * di JDK 17 (AGP 8.13.0 / Gradle 8.13 / compileSdk 36 / targetSdk 35), jadi sandbox
 * dijalankan pada SDK 35 — yang justru sama dengan targetSdk aplikasi.
 *
 * Test ini hanya memeriksa resource string, sehingga tidak bergantung pada level SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AI Hub", appName)
  }
}

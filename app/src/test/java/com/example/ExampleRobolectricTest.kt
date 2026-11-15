package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Setup cloud`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Setup cloud", appName)
  }

  @Test
  fun `default cloudflare account is seeded on first launch`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AccountPreferencesRepository(context)
    val accounts = repo.loadAccounts()
    assertTrue(accounts.isNotEmpty())
    val primary = accounts.first()
    assertEquals(AccountPreferencesRepository.DEFAULT_PROFILE_NAME, primary.profileName)
    assertEquals(AccountPreferencesRepository.DEFAULT_ACCOUNT_ID, primary.accountId)
    assertEquals(AccountPreferencesRepository.DEFAULT_API_TOKEN, primary.apiToken)
  }
}

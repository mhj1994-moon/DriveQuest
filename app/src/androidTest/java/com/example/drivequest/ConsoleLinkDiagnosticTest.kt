package com.example.drivequest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Regression guard: the installed app must not be allowed to fetch console HTTP data. */
@RunWith(AndroidJUnit4::class)
class ConsoleLinkDiagnosticTest {
    @Test fun consoleCleartextAccessIsDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
}

package com.apkscout.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkscout.android.settings.ReleaseChannelSettings

@Composable
fun SettingsScreen(
    settings: ReleaseChannelSettings,
    onDevChanged: (Boolean) -> Unit,
    onAlphaChanged: (Boolean) -> Unit,
    onBetaChanged: (Boolean) -> Unit,
    onRcChanged: (Boolean) -> Unit,
    onPrereleaseChanged: (Boolean) -> Unit
) {
    SettingsCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Release channels",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            SettingsSwitchRow(
                title = "Dev",
                subtitle = "Nightly, canary, snapshot, internal",
                checked = settings.includeDev,
                onCheckedChange = onDevChanged
            )

            SettingsSwitchRow(
                title = "Alpha",
                subtitle = null,
                checked = settings.includeAlpha,
                onCheckedChange = onAlphaChanged
            )

            SettingsSwitchRow(
                title = "Beta",
                subtitle = null,
                checked = settings.includeBeta,
                onCheckedChange = onBetaChanged
            )

            SettingsSwitchRow(
                title = "RC",
                subtitle = "Release candidate",
                checked = settings.includeRc,
                onCheckedChange = onRcChanged
            )

            SettingsSwitchRow(
                title = "Prerelease",
                subtitle = "Preview, pre-release, early access",
                checked = settings.includePrerelease,
                onCheckedChange = onPrereleaseChanged
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier.padding(18.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

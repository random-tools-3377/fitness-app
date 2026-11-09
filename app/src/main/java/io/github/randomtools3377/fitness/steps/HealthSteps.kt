package io.github.randomtools3377.fitness.steps

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.randomtools3377.fitness.core.StepCount
import io.github.randomtools3377.fitness.ui.FitnessTheme
import java.time.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toJavaInstant

/**
 * Steps from Health Connect (0.10.0), the phone's own store of health data, which works offline.
 * Only the step count is read, and only when the user has turned steps on and allowed it.
 */
object HealthSteps {
    val PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
    val PERMISSIONS: Set<String> = setOf(PERMISSION)

    /** Whether Health Connect is on this phone (built in from Android 14, an app before that). */
    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun allowed(context: Context): Boolean = available(context) &&
        runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }.getOrDefault(false)

    /**
     * The day's steps in 15-minute stretches. Health Connect adds up the phone's and a watch's
     * counts without counting the same steps twice. Empty if steps can't be read.
     */
    suspend fun day(context: Context, day: LocalDate, tz: TimeZone = TimeZone.currentSystemDefault()): List<StepCount> {
        if (!allowed(context)) return emptyList()
        val start = day.atStartOfDayIn(tz).toJavaInstant()
        val end = day.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toJavaInstant()
        return runCatching {
            HealthConnectClient.getOrCreate(context).aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Duration.ofMinutes(15),
                ),
            ).mapNotNull { slice ->
                slice.result[StepsRecord.COUNT_TOTAL]?.let { StepCount(slice.startTime.toEpochMilli(), slice.endTime.toEpochMilli(), it) }
            }
        }.getOrDefault(emptyList())
    }
}

/** Shown by Health Connect when the user asks why the app wants their steps. */
class StepsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Steps in Fitness App", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Fitness App reads only your step count, to show it on the day and add the calories your steps burn. " +
                                "It reads nothing else and never writes to Health Connect. Everything stays on this phone: " +
                                "the app has no internet access. You can turn steps off in the app's Settings, or take the " +
                                "permission back in Health Connect, at any time.",
                        )
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}

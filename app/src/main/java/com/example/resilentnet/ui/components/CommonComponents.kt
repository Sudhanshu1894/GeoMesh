package com.example.resilentnet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resilentnet.ui.theme.ResilientCardBg
import com.example.resilentnet.ui.theme.ResilientCardBorder
import com.example.resilentnet.ui.theme.ResilientErrorRed
import com.example.resilentnet.ui.theme.ResilientInfoBlue
import com.example.resilentnet.ui.theme.ResilientInputBg
import com.example.resilentnet.ui.theme.ResilientPrimaryGreen
import com.example.resilentnet.ui.theme.ResilientSoftGreen
import com.example.resilentnet.ui.theme.ResilientTextMain
import com.example.resilentnet.ui.theme.ResilientTextSecondary
import com.example.resilentnet.ui.theme.ResilientWarningAmber

/**
 * Reusable StatusBadge showing network engine state.
 * States: RUNNING, STOPPED, PAUSED, STARTING, ERROR.
 */
@Composable
fun StatusBadge(state: String, modifier: Modifier = Modifier) {
    val (bgColor, textColor, borderColor) = when (state.uppercase()) {
        "RUNNING" -> Triple(ResilientSoftGreen, ResilientPrimaryGreen, ResilientPrimaryGreen.copy(alpha = 0.4f))
        "PAUSED" -> Triple(Color(0xFFFFF7E6), ResilientWarningAmber, ResilientWarningAmber.copy(alpha = 0.4f))
        "STARTING" -> Triple(Color(0xFFEBF3FE), ResilientInfoBlue, ResilientInfoBlue.copy(alpha = 0.4f))
        "ERROR" -> Triple(Color(0xFFFDE8E9), ResilientErrorRed, ResilientErrorRed.copy(alpha = 0.4f))
        else -> Triple(ResilientInputBg, ResilientTextSecondary, ResilientCardBorder)
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = state.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Clean Light Metric Card for the 2x2 dashboard telemetry grid.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color = ResilientTextMain,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ResilientTextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = ResilientTextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Reusable Empty State card with informative guidance and troubleshooting points.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    description: String,
    troubleshootingHints: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ResilientCardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ResilientCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = icon,
                fontSize = 36.sp
            )
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ResilientTextMain
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = ResilientTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            if (troubleshootingHints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ResilientInputBg, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Troubleshooting Hints:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ResilientTextMain
                    )
                    troubleshootingHints.forEach { hint ->
                        Text(
                            text = "• $hint",
                            fontSize = 11.sp,
                            color = ResilientTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Logo aplikasi: kotak membulat berwarna aksen dengan huruf "AI" tebal di tengah.
 * Tanpa font kustom dan tanpa aset gambar sehingga warnanya mengikuti tema
 * (termasuk dynamic color) dan kontrasnya terjaga di mode terang maupun gelap.
 */
@Composable
fun AiLogo(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(containerColor)
            .semantics { contentDescription = "Logo AI Hub" }
            .testTag("ai_logo"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AI",
            color = contentColor,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.44f).sp,
            letterSpacing = (-0.5).sp,
            maxLines = 1
        )
    }
}

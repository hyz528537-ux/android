package ui.components.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun EventActionLoading(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(
                width = 2.dp,
                color = UIKit.colorScheme.background.content,
                shape = CircleShape
            )
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = UIKit.colorScheme.icon.tertiary,
                    shape = CircleShape
                )
                .padding(4.dp),
            strokeWidth = 2.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.32f)
        )
    }
}
package ui.components.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.size.Size
import ui.components.image.AsyncImage
import ui.theme.UIKit

@Composable
internal fun EventIconAction(
    action: UiEvent.Item.Action
) {

    val iconSize = 44.dp
    val colorScheme = UIKit.colorScheme

    val iconUrl = action.iconUrl
    val pictureUrl = if (!action.spam) action.imageUrl else null

    Box(
        modifier = Modifier
            .size(iconSize)
            .clip(CircleShape)
            .background(colorScheme.background.contentTint),
        contentAlignment = Alignment.Center
    ) {
        if (pictureUrl != null) {
            AsyncImage(
                modifier = Modifier.matchParentSize(),
                url = pictureUrl,
                size = 128,
                contentScale = ContentScale.Crop
            )
        } else if (iconUrl != null) {
            AsyncImage(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
                url = iconUrl,
                size = Size.ORIGINAL,
                contentScale = ContentScale.Inside,
                colorFilter = UIKit.colorScheme.icon.secondaryColorFilter,
                crossfadeDuration = 0
            )
        }

        if (action.state == UiEvent.Item.Action.State.Pending) {
            EventActionLoading(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
            )
        }
    }
}
package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subkiru.subkiru.ui.theme.SubKiruTheme
import com.subkiru.subkiru.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionListRow(
    serviceName: String,
    supportingText: String,
    amountText: String,
    amountSuffix: String,
    modifier: Modifier = Modifier,
    logoResId: Int? = null,
    categoryColorHex: String? = null,
    amountColor: Color? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongClick?.invoke() },
                )
                .padding(vertical = ROW_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServiceAvatar(
                name = serviceName,
                logoResId = logoResId,
                categoryColorHex = categoryColorHex,
                size = ROW_AVATAR_SIZE,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor ?: MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = amountSuffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private val ROW_VERTICAL_PADDING = 14.dp
private val ROW_CONTENT_SPACING = 12.dp
private val ROW_AVATAR_SIZE = 40.dp
private val PREVIEW_HORIZONTAL_PADDING = 16.dp

@Preview(showBackground = true)
@Composable
private fun SubscriptionListRowPreview() {
    SubKiruTheme {
        SubscriptionListRow(
            serviceName = "Netflix",
            supportingText = "次回 6月15日",
            amountText = "¥1,490",
            amountSuffix = "/月",
            modifier = Modifier.padding(horizontal = PREVIEW_HORIZONTAL_PADDING),
        )
    }
}

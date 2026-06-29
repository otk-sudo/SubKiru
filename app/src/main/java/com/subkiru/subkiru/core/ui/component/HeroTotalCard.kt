package com.subkiru.subkiru.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.subkiru.subkiru.ui.theme.HeroCardBackground
import com.subkiru.subkiru.ui.theme.OnHeroCard
import com.subkiru.subkiru.ui.theme.OnHeroCardMuted
import com.subkiru.subkiru.ui.theme.SubKiruTheme

@Composable
fun HeroTotalCard(
    label: String,
    amount: String,
    annualAmount: String,
    subscriptionCount: Int,
    modifier: Modifier = Modifier,
    actionContent: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HeroCardBackground, RoundedCornerShape(HERO_CORNER_RADIUS))
            .padding(HERO_INNER_PADDING),
        verticalArrangement = Arrangement.spacedBy(HERO_CONTENT_SPACING),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = OnHeroCardMuted,
            )
            actionContent()
        }

        Text(
            text = amount,
            style = MaterialTheme.typography.displayLarge,
            color = OnHeroCard,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "年間換算 $annualAmount",
                style = MaterialTheme.typography.bodySmall,
                color = OnHeroCardMuted,
            )
            Text(
                text = "契約数 ${subscriptionCount}件",
                style = MaterialTheme.typography.bodySmall,
                color = OnHeroCardMuted,
            )
        }
    }
}

private val HERO_CORNER_RADIUS = 20.dp
private val HERO_INNER_PADDING = 20.dp
private val HERO_CONTENT_SPACING = 8.dp
private val PREVIEW_PADDING = 16.dp

@Preview(showBackground = true)
@Composable
private fun HeroTotalCardPreview() {
    SubKiruTheme {
        HeroTotalCard(
            label = "今月の合計",
            amount = "¥44,216",
            annualAmount = "¥530,592",
            subscriptionCount = 8,
            modifier = Modifier.padding(PREVIEW_PADDING),
        )
    }
}

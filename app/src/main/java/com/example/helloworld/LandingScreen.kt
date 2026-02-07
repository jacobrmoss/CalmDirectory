package com.example.helloworld

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.helloworld.data.QuickLocation
import com.mudita.mmd.components.chips.AssistChipMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD

val poiCategories = listOf(
    "Gas Stations",
    "Restaurants",
    "Entertainment",
    "Coffee",
    "Shopping",
    "Hotels"
)

@Composable
fun LandingScreen(
    modifier: Modifier = Modifier,
    quickLocations: List<QuickLocation>,
    onAddQuickLocation: () -> Unit,
    onQuickLocationClicked: (QuickLocation) -> Unit,
    onCategorySelected: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clip(CircleShape)
                        .clickable { onAddQuickLocation() }
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Quick Location",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            items(quickLocations) { location ->
                AssistChipMMD(
                    onClick = { onQuickLocationClicked(location) },
                    label = {
                        Text(
                            text = location.label,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        val icon = when (location.label.lowercase()) {
                            "home" -> Icons.Outlined.Home
                            "work" -> Icons.Outlined.WorkOutline
                            else -> Icons.Outlined.Place
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        HorizontalDividerMMD(
            thickness = 1.dp,
            modifier = Modifier.padding(start = 16.dp)
        )

        LazyColumnMMD(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(poiCategories) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(category) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getIconForCategory(category),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Text(category, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null
                    )
                }

                if (category != poiCategories.last()) {
                    DashedDivider(
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

fun getIconForCategory(category: String): ImageVector {
    return when (category) {
        "Gas Stations" -> Icons.Outlined.LocalGasStation
        "Restaurants" -> Icons.Outlined.Restaurant
        "Entertainment" -> Icons.Outlined.Movie
        "Coffee" -> Icons.Outlined.LocalCafe
        "Shopping" -> Icons.Outlined.ShoppingCart
        "Hotels" -> Icons.Outlined.Hotel
        else -> throw IllegalArgumentException("Unknown category: $category")
    }
}
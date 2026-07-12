package com.mtgscanner.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtgscanner.data.ScannedCardDatabase
import com.mtgscanner.model.ScannedCard
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow

/**
 * CollectionScreen: Browse and manage scanned Magic cards.
 * Displays cards in a grid with search, filter by set, and collection statistics.
 *
 * @param database Room database for card queries
 * @param onEditCard Callback when user taps a card to edit quantity
 * @param modifier Compose modifier
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CollectionScreen(
    database: ScannedCardDatabase,
    onEditCard: (card: ScannedCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterSet by remember { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(SortOrder.NAME_ASC) }

    // Fetch all cards from database (Flow-based reactive updates)
    val allCards by database.scannedCardDao().getAllCards()
        .map { entities -> entities.map { it.toScannedCard() } }
        .collectAsState(initial = emptyList())

    // Filter and sort cards
    val filteredCards = when (sortOrder) {
        SortOrder.NAME_ASC -> allCards
            .filter { card ->
                card.cardName.contains(searchQuery, ignoreCase = true) &&
                    (filterSet == null || card.setCode == filterSet)
            }
            .sortedBy { it.cardName.lowercase() }

        SortOrder.NAME_DESC -> allCards
            .filter { card ->
                card.cardName.contains(searchQuery, ignoreCase = true) &&
                    (filterSet == null || card.setCode == filterSet)
            }
            .sortedByDescending { it.cardName.lowercase() }

        SortOrder.SET_ASC -> allCards
            .filter { card ->
                card.cardName.contains(searchQuery, ignoreCase = true) &&
                    (filterSet == null || card.setCode == filterSet)
            }
            .sortedBy { it.setCode.lowercase() }

        SortOrder.DATE_NEWEST -> allCards
            .filter { card ->
                card.cardName.contains(searchQuery, ignoreCase = true) &&
                    (filterSet == null || card.setCode == filterSet)
            }
            .sortedByDescending { it.scannedTimestamp }
    }

    val uniqueSets = allCards.map { it.setCode }.distinct().sorted()
    val totalCards = allCards.sumOf { it.quantity }
    val uniqueCardCount = allCards.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1976D2))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "My Collection",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    StatBox(label = "Total Cards", value = "$totalCards")
                    StatBox(label = "Unique", value = "$uniqueCardCount")
                    StatBox(label = "Sets", value = "${uniqueSets.size}")
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Filter and Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterSet == null,
                    onClick = { filterSet = null },
                    label = { Text("All Sets") }
                )

                uniqueSets.forEach { set ->
                    FilterChip(
                        selected = filterSet == set,
                        onClick = { filterSet = set },
                        label = { Text(set) }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Sort dropdown
                var expandSort by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expandSort = !expandSort },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sort", fontSize = 12.sp)
                    }

                    DropdownMenu(
                        expanded = expandSort,
                        onDismissRequest = { expandSort = false }
                    ) {
                        SortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    sortOrder = order
                                    expandSort = false
                                }
                            )
                        }
                    }
                }
            }

            Divider()

            // Card Grid
            if (filteredCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "No cards",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Text(
                            text = "No cards found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        if (searchQuery.isNotEmpty() || filterSet != null) {
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    filterSet = null
                                }
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCards) { card ->
                        CardGridItem(
                            card = card,
                            onClick = { onEditCard(card) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * StatBox: Small stat display (cards, sets, etc.)
 */
@Composable
fun StatBox(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/**
 * CardGridItem: Single card in the collection grid.
 */
@Composable
fun CardGridItem(
    card: ScannedCard,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .aspectRatio(0.7f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Card image
            if (!card.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.cardName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "No image",
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                }
            }

            // Overlay with card info
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Bottom info panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = card.cardName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = card.setCode,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "×${card.quantity}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Cyan
                    )
                }
            }
        }
    }
}

/**
 * SortOrder: Enum for collection sorting options.
 */
enum class SortOrder(val label: String) {
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    SET_ASC("Set Code"),
    DATE_NEWEST("Recently Scanned")
}

package com.vythera.vyxelapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun CompareScreen(
    leftRepo  : GitHubRepo,
    rightRepo : GitHubRepo?,
    searchResults : List<GitHubRepo>,
    onSearch : (String) -> Unit,
    onPickRight : (GitHubRepo) -> Unit,
    onBack : () -> Unit
) {
    val t = LocalTheme.current
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(t.bgPrimary)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(t.bgSurface).statusBarSpace()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = t.textPrimary)
            }
            Text("Compare Apps", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = t.textPrimary)
        }

        if (rightRepo == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Comparing with: ${leftRepo.name}", fontSize = 13.sp, color = t.textSecondary)
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                    placeholder = { Text("Search second app to compare…", color = t.textSecondary) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = t.textSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = t.bgSurfaceAlt, unfocusedContainerColor = t.bgSurfaceAlt,
                        focusedIndicatorColor = t.accent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = t.textPrimary, unfocusedTextColor = t.textPrimary
                    )
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchResults.filter { it.id != leftRepo.id }) { repo ->
                        AppListTile(repo = repo, onClick = { onPickRight(repo) })
                    }
                }
            }
        } else {
            CompareSideBySide(left = leftRepo, right = rightRepo)
        }
    }
}

@Composable
private fun CompareSideBySide(left: GitHubRepo, right: GitHubRepo) {
    val t = LocalTheme.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompareCardHeader(left, modifier = Modifier.weight(1f))
                CompareCardHeader(right, modifier = Modifier.weight(1f))
            }
        }
        item {
            CompareRow("Stars", "★ ${formatStars(left.stargazers_count)}", "★ ${formatStars(right.stargazers_count)}",
                leftBetter = left.stargazers_count > right.stargazers_count)
        }
        item {
            CompareRow("Forks", formatStars(left.forks_count), formatStars(right.forks_count),
                leftBetter = left.forks_count > right.forks_count)
        }
        item {
            CompareRow("Language", left.language ?: "—", right.language ?: "—", leftBetter = null)
        }
        item {
            val leftFeatures  = detectFeatures(left)
            val rightFeatures = detectFeatures(right)
            val all = (leftFeatures + rightFeatures).distinct().sorted()
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(t.bgSurface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Features", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = t.textPrimary)
                Spacer(Modifier.height(4.dp))
                all.forEach { feature ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(feature, fontSize = 12.sp, color = t.textPrimary, modifier = Modifier.weight(1f))
                        Icon(
                            if (feature in leftFeatures) Icons.Rounded.Check else Icons.Rounded.Close,
                            null,
                            tint = if (feature in leftFeatures) GreenOk else t.textSecondary.copy(0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(20.dp))
                        Icon(
                            if (feature in rightFeatures) Icons.Rounded.Check else Icons.Rounded.Close,
                            null,
                            tint = if (feature in rightFeatures) GreenOk else t.textSecondary.copy(0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareCardHeader(repo: GitHubRepo, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.bgSurface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = repo.iconUrlOrNull, contentDescription = null,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(repo.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = t.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("@${repo.owner.login}", fontSize = 11.sp, color = t.textSecondary, maxLines = 1)
    }
}

@Composable
private fun CompareRow(label: String, leftValue: String, rightValue: String, leftBetter: Boolean?) {
    val t = LocalTheme.current
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(t.bgSurface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = t.textSecondary, modifier = Modifier.weight(1f))
        Text(leftValue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (leftBetter == true) GreenOk else t.textPrimary,
            modifier = Modifier.weight(1f))
        Text(rightValue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (leftBetter == false) GreenOk else t.textPrimary,
            modifier = Modifier.weight(1f))
    }
}

private fun detectFeatures(repo: GitHubRepo): List<String> {
    val text = "${repo.displayName} ${repo.description ?: ""}".lowercase()
    val features = mutableListOf<String>()
    if ("login" in text || "account" in text || "auth" in text) features.add("Login support")
    if ("sponsorblock" in text)   features.add("SponsorBlock")
    if ("download" in text)        features.add("Downloads")
    if ("offline" in text)         features.add("Offline mode")
    if ("ad-free" in text || "no ads" in text || "adblock" in text) features.add("Ad-free")
    if ("pip" in text || "picture-in-picture" in text || "background" in text) features.add("Background play")
    if ("subtitle" in text || "captions" in text) features.add("Subtitles")
    if ("dark" in text || "theme" in text) features.add("Dark theme")
    if ("playlist" in text)        features.add("Playlists")
    if ("cast" in text || "chromecast" in text) features.add("Cast support")
    if ("import" in text || "export" in text) features.add("Import/Export")
    if ("encrypt" in text || "privacy" in text) features.add("Privacy/Encryption")
    return features
}
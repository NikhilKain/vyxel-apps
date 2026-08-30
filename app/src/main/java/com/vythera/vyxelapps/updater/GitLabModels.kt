package com.vythera.vyxelapps.updater

data class GitLabApp(val packageName: String, val user: String, val repo: String)

val GitLabApps = listOf(
    GitLabApp("com.aurora.store", "AuroraOSS", "AuroraStore")
)

data class GitLabAuthor(val avatar_url: String = "")
data class GitLabAsset(val format: String, val url: String)
data class GitLabLink(val url: String)
data class GitLabAssets(
    val sources: List<GitLabAsset> = emptyList(),
    val links: List<GitLabLink> = emptyList()
)
data class GitLabRelease(
    val tag_name: String,
    val description: String = "",
    val assets: GitLabAssets,
    val author: GitLabAuthor = GitLabAuthor()
)

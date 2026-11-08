package com.example.data.model

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val currentVersionName: String,
    val downloadUrl: String,
    val changelogSnippet: String,
    val publishedAt: String = ""
)

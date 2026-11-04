package com.comfyremote.app.data.cloud

/**
 * Model catalogues are pulled from the provider, never seeded from a hard-coded
 * list. The first successful pull is persisted so later sessions can open the
 * app offline; a saved empty list stays empty (the user removed every model).
 */
internal suspend fun pulledModels(
    settings: CloudProviderSettingsStore,
    providerId: String,
    baseUrl: String,
    token: String,
    discover: suspend () -> List<String>,
): List<String> {
    val saved = settings.account(providerId, baseUrl).models
    if (settings.hasSavedModels(providerId)) return saved
    // Surface discovery/auth failures to the caller so Create can label the
    // failing provider instead of silently showing an empty catalogue.
    val discovered = discover()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (discovered.isNotEmpty()) settings.save(providerId, baseUrl, token, discovered)
    return discovered
}

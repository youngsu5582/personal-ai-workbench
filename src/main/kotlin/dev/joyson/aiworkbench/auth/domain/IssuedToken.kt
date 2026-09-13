package dev.joyson.aiworkbench.auth.domain

data class IssuedToken(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)

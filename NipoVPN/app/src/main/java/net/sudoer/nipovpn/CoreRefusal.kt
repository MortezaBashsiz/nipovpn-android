package net.sudoer.nipo

enum class Refusal(val message: String) {
    // 403: usage limit reached (the spec also counts a disabled account).
    QUOTA_OR_DISABLED("Your account's data allowance has been exhausted or your account has been disabled."),
    // 400: a disabled user's token, a wrong token, or settings that don't match the server.
    REJECTED("The server rejected this connection. Your account may have been disabled, or this profile's token or settings don't match the server."),
}

// Matches the "[Server Refused] [<status>]" line the patched agent logs.
fun coreRefusal(line: String): Refusal? = when {
    "[Server Refused] [403" in line -> Refusal.QUOTA_OR_DISABLED
    "[Server Refused] [400" in line -> Refusal.REJECTED
    else -> null
}

package net.sudoer.nipo

/** Why the server turned this account down; [message] is what the user is told. */
enum class Refusal(val message: String) {
    // 403: the usage limit is reached (the spec also lists a disabled account here).
    QUOTA_OR_DISABLED("Your account's data allowance has been exhausted or your account has been disabled."),
    // 400: a disabled user's token, a wrong token, or settings that don't match the server.
    REJECTED("The server rejected this connection. Your account may have been disabled, or this profile's token or settings don't match the server."),
}

// The agent logs "[Server Refused] [<status line>]" when the server turns a
// request down; anything else in its log is not a refusal.
fun coreRefusal(line: String): Refusal? = when {
    "[Server Refused] [403" in line -> Refusal.QUOTA_OR_DISABLED
    "[Server Refused] [400" in line -> Refusal.REJECTED
    else -> null
}

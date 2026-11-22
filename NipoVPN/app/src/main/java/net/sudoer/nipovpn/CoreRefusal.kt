package net.sudoer.nipo

// The agent logs "[Server Refused] [<status line>]" when the server turns a
// request down; this maps that line to what the user should be told.
fun coreRefusalMessage(line: String): String? = when {
    "[Server Refused] [403" in line ->
        "Your account's data allowance has been exhausted or your account has been disabled."
    "[Server Refused] [400" in line ->
        "The server rejected this connection. Your account may have been disabled, or this profile's token or settings don't match the server."
    else -> null
}

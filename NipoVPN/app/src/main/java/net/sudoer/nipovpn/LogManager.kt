package net.sudoer.nipo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LogManager {
    private const val MAX_LINES = 500
    private val lines = ArrayDeque<String>()

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs

    @Synchronized
    fun append(line: String) {
        lines.addLast(line)

        while (lines.size > MAX_LINES) {
            lines.removeFirst()
        }

        _logs.value = lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
        _logs.value = ""
    }
}

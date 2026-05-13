package dev.local.smsforwarder.sms

/** Matches SMS senders against an optional comma or newline separated allowlist. */
object SenderFilter {
    fun isAllowed(sender: String, filter: String): Boolean {
        val entries = filter
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (entries.isEmpty()) return true

        val normalizedSender = normalize(sender)
        return entries.any { entry ->
            val normalizedEntry = normalize(entry)
            sender.equals(entry, ignoreCase = true) ||
                normalizedSender == normalizedEntry ||
                normalizedSender.endsWith(normalizedEntry)
        }
    }

    private fun normalize(value: String): String =
        value.filter { it.isDigit() || it == '+' }.removePrefix("+")
}

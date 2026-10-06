package com.example.zammad.core

import android.text.Html
import com.example.zammad.model.ReferenceData
import com.example.zammad.model.Ticket
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val closedStateNames = setOf("closed", "removed", "merged")

fun isClosedStateName(name: String): Boolean = closedStateNames.contains(name.lowercase())

fun isClosed(ticket: Ticket): Boolean {
    val name = ticket.state?.lowercase() ?: ""
    if (name.isEmpty()) return false
    return isClosedStateName(name)
}

fun stateNameOf(ticket: Ticket, ref: ReferenceData): String =
    ticket.state?.takeIf { it.isNotBlank() && it != "-" }
        ?: ref.states.firstOrNull { it.id == ticket.stateId }?.name
        ?: ""

fun priorityNameOf(ticket: Ticket, ref: ReferenceData): String =
    ref.priorities.firstOrNull { it.id == ticket.priorityId }?.name ?: ""

fun groupNameOf(ticket: Ticket, ref: ReferenceData): String =
    ticket.group?.takeIf { it.isNotBlank() && it != "-" }
        ?: ref.groups.firstOrNull { it.id == ticket.groupId }?.name
        ?: ""

/** owner_id = 1 is the system user → "Unassigned". */
fun ownerNameOf(ticket: Ticket): String {
    if (ticket.ownerId == null || ticket.ownerId == 1) return L10n.t("ticket.assign.unassigned")
    val owner = ticket.owner?.takeIf { it.isNotBlank() && it != "-" }
    return owner ?: L10n.t("common.unknown")
}

fun displayName(value: String?): String {
    val v = value?.trim() ?: ""
    if (v.isEmpty() || v == "-") return "—"
    return v
}

/** "5 min ago" style stamp, localized by the app language. */
fun relativeDate(iso: String?): String {
    val raw = iso ?: return ""
    val instant = try {
        Instant.parse(raw)
    } catch (_: Exception) {
        return ""
    }
    val mins = Duration.between(instant, Instant.now()).toMinutes()
    val ru = L10n.code == "ru"
    return when {
        mins < 1 -> if (ru) "только что" else "just now"
        mins < 60 -> if (ru) "$mins мин назад" else "${mins}m ago"
        mins < 60 * 24 -> {
            val h = mins / 60
            if (ru) "$h ч назад" else "${h}h ago"
        }
        mins < 60 * 24 * 7 -> {
            val d = mins / (60 * 24)
            when {
                d == 1L && ru -> "вчера"
                d == 1L -> "yesterday"
                ru -> "$d дн назад"
                else -> "${d}d ago"
            }
        }
        else -> DateTimeFormatter.ofPattern("dd.MM.yyyy")
            .format(instant.atZone(ZoneId.systemDefault()))
    }
}

/** Absolute date/time for the ticket header. */
fun dateTimeOf(iso: String?): String {
    val raw = iso ?: return "—"
    val instant = try {
        Instant.parse(raw)
    } catch (_: Exception) {
        return raw
    }
    return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .format(instant.atZone(ZoneId.systemDefault()))
}

/** Strips HTML from article bodies. */
fun plainText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

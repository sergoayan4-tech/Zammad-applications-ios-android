package com.example.zammad.model

import com.example.zammad.api.flexBool
import com.example.zammad.api.flexInt
import com.example.zammad.api.flexString
import com.example.zammad.api.jsonObjects
import org.json.JSONObject

data class Ticket(
    val id: Int,
    val number: String,
    val title: String,
    val groupId: Int?,
    val stateId: Int?,
    val priorityId: Int?,
    val ownerId: Int?,
    val customerId: Int?,
    val organizationId: Int?,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val state: String?,
    val group: String?,
    val owner: String?,
    val customer: String?,
    val organization: String?
) {
    val displayNumber: String get() = "#$number"
    val displayTitle: String get() = if (title.isBlank()) displayNumber else title

    companion object {
        fun fromJson(o: JSONObject): Ticket = Ticket(
            id = o.flexInt("id") ?: 0,
            number = o.flexString("number") ?: (o.flexInt("id") ?: 0).toString(),
            title = o.flexString("title") ?: "",
            groupId = o.flexInt("group_id"),
            stateId = o.flexInt("state_id"),
            priorityId = o.flexInt("priority_id"),
            ownerId = o.flexInt("owner_id"),
            customerId = o.flexInt("customer_id"),
            organizationId = o.flexInt("organization_id"),
            note = o.flexString("note"),
            createdAt = o.flexString("created_at"),
            updatedAt = o.flexString("updated_at"),
            state = o.flexString("state"),
            group = o.flexString("group"),
            owner = o.flexString("owner"),
            customer = o.flexString("customer"),
            organization = o.flexString("organization")
        )
    }
}

data class ArticleAttachment(
    val id: Int,
    val filename: String,
    val size: Long?,
    val contentType: String?
) {
    val extension: String get() = filename.substringAfterLast('.', "").lowercase()

    companion object {
        fun fromJson(o: JSONObject): ArticleAttachment = ArticleAttachment(
            id = o.flexInt("id") ?: 0,
            filename = o.flexString("filename") ?: "",
            size = o.flexLongSafe("size"),
            contentType = o.flexString("content_type")
        )

        private fun JSONObject.flexLongSafe(key: String): Long? =
            if (has(key) && !isNull(key)) flexInt(key)?.toLong() ?: (get(key) as? Number)?.toLong() else null
    }
}

data class TicketArticle(
    val id: Int,
    val ticketId: Int?,
    val type: String?,
    val sender: String?,
    val from: String?,
    val subject: String?,
    val body: String?,
    val contentType: String?,
    val isInternal: Boolean?,
    val createdAt: String?,
    val createdBy: String?,
    val attachments: List<ArticleAttachment>
) {
    val isFromCustomer: Boolean get() = sender == "customer"
    val isFromSystem: Boolean get() = sender == "system"

    companion object {
        fun fromJson(o: JSONObject): TicketArticle = TicketArticle(
            id = o.flexInt("id") ?: 0,
            ticketId = o.flexInt("ticket_id"),
            type = o.flexString("type"),
            sender = o.flexString("sender"),
            from = o.flexString("from"),
            subject = o.flexString("subject"),
            body = o.flexString("body") ?: o.flexString("message_plain"),
            contentType = o.flexString("content_type"),
            isInternal = o.flexBool("internal"),
            createdAt = o.flexString("created_at"),
            createdBy = o.flexString("created_by"),
            attachments = o.optJSONArray("attachments")
                ?.jsonObjects()
                ?.map { ArticleAttachment.fromJson(it) }
                ?: emptyList()
        )
    }
}

data class ZammadState(val id: Int, val name: String, val active: Boolean) {
    companion object {
        fun fromJson(o: JSONObject) = ZammadState(
            id = o.flexInt("id") ?: 0,
            name = o.flexString("name") ?: "",
            active = o.flexBool("active") ?: true
        )
    }
}

data class ZammadPriority(val id: Int, val name: String, val active: Boolean) {
    companion object {
        fun fromJson(o: JSONObject) = ZammadPriority(
            id = o.flexInt("id") ?: 0,
            name = o.flexString("name") ?: "",
            active = o.flexBool("active") ?: true
        )
    }
}

data class ZammadGroup(val id: Int, val name: String, val active: Boolean) {
    companion object {
        fun fromJson(o: JSONObject) = ZammadGroup(
            id = o.flexInt("id") ?: 0,
            name = o.flexString("name") ?: "",
            active = o.flexBool("active") ?: true
        )
    }
}

data class ZammadUser(
    val id: Int,
    val firstname: String?,
    val lastname: String?,
    val email: String?,
    val login: String?,
    val active: Boolean
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstname, lastname)
                .filter { it.isNotBlank() && it != "-" }
                .joinToString(" ")
            if (name.isNotBlank()) return name
            if (!email.isNullOrBlank() && email != "-") return email
            return login ?: "-"
        }

    companion object {
        fun fromJson(o: JSONObject) = ZammadUser(
            id = o.flexInt("id") ?: 0,
            firstname = o.flexString("firstname"),
            lastname = o.flexString("lastname"),
            email = o.flexString("email"),
            login = o.flexString("login"),
            active = o.flexBool("active") ?: true
        )
    }
}

/** Lookups loaded once after signing in. */
data class ReferenceData(
    val states: List<ZammadState> = emptyList(),
    val priorities: List<ZammadPriority> = emptyList(),
    val groups: List<ZammadGroup> = emptyList(),
    val users: List<ZammadUser> = emptyList()
) {
    val activeStates: List<ZammadState> get() = states.filter { it.active }
    val activePriorities: List<ZammadPriority> get() = priorities.filter { it.active }
    val activeGroups: List<ZammadGroup> get() = groups.filter { it.active }
}

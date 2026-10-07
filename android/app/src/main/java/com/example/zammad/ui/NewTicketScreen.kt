package com.example.zammad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.model.Ticket
import com.example.zammad.session.SessionStore
import com.example.zammad.vm.NewTicketModel
import kotlinx.coroutines.launch

/** New ticket form (mirrors iOS NewTicketView). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTicketScreen(
    session: SessionStore,
    onCreated: (Ticket) -> Unit
) {
    val model = remember { NewTicketModel() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.reference) {
        model.applyDefaults(session.reference)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L10n.t("new.title"), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val ref = session.reference

            OutlinedTextField(
                value = model.title,
                onValueChange = {
                    model.title = it
                    if (model.validationKey == "new.error.title") model.validationKey = null
                },
                label = { Text(L10n.t("new.subject")) },
                placeholder = { Text(L10n.t("new.subject.placeholder")) },
                singleLine = true,
                isError = model.validationKey == "new.error.title",
                supportingText = {
                    if (model.validationKey == "new.error.title") {
                        Text(L10n.t("new.error.title"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            SelectField(
                label = L10n.t("new.group"),
                value = ref.groups.firstOrNull { it.id == model.groupId }?.name
                    ?: if (ref.activeGroups.isEmpty()) "—" else "",
                options = ref.activeGroups.map { it.id to it.name },
                selectedId = model.groupId,
                isError = model.validationKey == "new.error.group",
                errorText = L10n.t("new.error.group")
            ) { model.groupId = it }

            OutlinedTextField(
                value = model.customerEmail,
                onValueChange = { model.customerEmail = it },
                label = { Text(L10n.t("new.customer")) },
                placeholder = { Text(L10n.t("new.customer.placeholder")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Row2(
                first = {
                    SelectField(
                        label = L10n.t("new.priority"),
                        value = ref.priorities.firstOrNull { it.id == model.priorityId }?.name ?: "—",
                        options = ref.activePriorities.map { it.id to it.name },
                        selectedId = model.priorityId
                    ) { model.priorityId = it }
                },
                second = {
                    SelectField(
                        label = L10n.t("new.state"),
                        value = ref.states.firstOrNull { it.id == model.stateId }?.name ?: "—",
                        options = ref.activeStates.map { it.id to it.name },
                        selectedId = model.stateId
                    ) { model.stateId = it }
                }
            )

            // ---- owner (assignee) ----
            Column {
                Text(
                    L10n.t("ticket.owner"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row2(
                    first = {
                        OutlinedButton(
                            onClick = {
                                model.ownerId = null
                                model.ownerName = null
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                L10n.t("ticket.assign.me"),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    },
                    second = {
                        OutlinedButton(
                            onClick = {
                                model.ownerId = 1
                                model.ownerName = null
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                L10n.t("ticket.assign.unassigned"),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
                val ownerLabel = when {
                    model.ownerId == null -> {
                        val me = session.currentUser?.displayName ?: ""
                        if (me.isEmpty()) L10n.t("ticket.assign.me")
                        else L10n.t("ticket.assign.me") + ": " + me
                    }
                    model.ownerId == 1 -> L10n.t("ticket.assign.unassigned")
                    else -> model.ownerName ?: ("#" + model.ownerId)
                }
                Text(
                    ownerLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model.ownerQuery,
                    onValueChange = {
                        model.ownerQuery = it
                        model.searchOwners(it, session)
                    },
                    placeholder = { Text(L10n.t("ticket.assign.search")) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (model.ownersLoading) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            L10n.t("common.loading"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (model.ownerQuery.isNotBlank() && model.owners.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("ticket.assign.empty"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                model.owners.forEach { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.selectOwner(user) }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(user.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            val email = user.email
                            if (!email.isNullOrBlank() && email != "-") {
                                Text(
                                    email,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = model.body,
                onValueChange = {
                    model.body = it
                    if (model.validationKey == "new.error.message") model.validationKey = null
                },
                label = { Text(L10n.t("new.message")) },
                placeholder = { Text(L10n.t("new.message.placeholder")) },
                minLines = 5,
                isError = model.validationKey == "new.error.message",
                supportingText = {
                    if (model.validationKey == "new.error.message") {
                        Text(L10n.t("new.error.message"))
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            val err = errorText(model.errorMessage)
            if (err != null) {
                Text(err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    scope.launch {
                        val created = model.create(session)
                        if (created != null) onCreated(created)
                    }
                },
                enabled = !model.creating,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (model.creating) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(" " + L10n.t("new.creating"), fontSize = 15.sp)
                } else {
                    Text(
                        L10n.t("new.submit"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Row2(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}

/** Labeled dropdown field (label above, menu on tap). */
@Composable
private fun SelectField(
    label: String,
    value: String,
    options: List<Pair<Int, String>>,
    selectedId: Int?,
    isError: Boolean = false,
    errorText: String? = null,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            label,
            fontSize = 12.sp,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(name, maxLines = 2, fontSize = 14.sp)
                        },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                        trailingIcon = {
                            if (id == selectedId) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
        if (isError && errorText != null) {
            Spacer(Modifier.height(3.dp))
            Text(errorText, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

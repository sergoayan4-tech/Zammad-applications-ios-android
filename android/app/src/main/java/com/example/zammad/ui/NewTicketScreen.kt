package com.example.zammad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
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

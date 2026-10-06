package com.example.zammad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zammad.core.L10n
import com.example.zammad.core.ownerNameOf
import com.example.zammad.session.SessionStore
import com.example.zammad.vm.TicketDetailModel
import kotlinx.coroutines.launch

/** Bottom sheet with ticket actions: state, priority, group, owner (iOS TicketActionsSheet). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TicketActionsSheet(
    session: SessionStore,
    model: TicketDetailModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ref = session.reference
    val ticket = model.ticket
    var ownerQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                L10n.t("ticket.actions"),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            val err = errorText(model.errorMessage)
            if (err != null) {
                Spacer(Modifier.height(8.dp))
                Text(err, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }

            // ---- state ----
            SectionLabel(L10n.t("ticket.changeState"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ref.activeStates.forEach { s ->
                    ActionChip(
                        label = s.name,
                        selected = ticket.state?.equals(s.name, ignoreCase = true) == true
                    ) {
                        scope.launch { model.setState(s.id) }
                    }
                }
            }

            // ---- priority ----
            Spacer(Modifier.height(18.dp))
            SectionLabel(L10n.t("ticket.changePriority"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ref.activePriorities.forEach { p ->
                    val current = com.example.zammad.core.priorityNameOf(ticket, ref)
                    ActionChip(
                        label = p.name,
                        selected = current.equals(p.name, ignoreCase = true)
                    ) {
                        scope.launch { model.setPriority(p.id) }
                    }
                }
            }

            // ---- group ----
            Spacer(Modifier.height(18.dp))
            SectionLabel(L10n.t("ticket.changeGroup"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ref.activeGroups.forEach { g ->
                    val current = com.example.zammad.core.groupNameOf(ticket, ref)
                    ActionChip(
                        label = g.name,
                        selected = current.equals(g.name, ignoreCase = true)
                    ) {
                        scope.launch { model.setGroup(g.id) }
                    }
                }
            }

            // ---- assign ----
            Spacer(Modifier.height(18.dp))
            SectionLabel(L10n.t("ticket.assign"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val me = session.currentUser?.id ?: return@OutlinedButton
                    scope.launch { model.setOwner(me) }
                }) {
                    Text(L10n.t("ticket.assign.me"), fontSize = 13.sp)
                }
                OutlinedButton(onClick = {
                    scope.launch { model.setOwner(1) }
                }) {
                    Text(L10n.t("ticket.assign.unassigned"), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                L10n.t("ticket.owner") + ": " + ownerNameOf(ticket),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ownerQuery,
                onValueChange = {
                    ownerQuery = it
                    model.searchOwners(it)
                },
                placeholder = { Text(L10n.t("ticket.assign.search")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (model.ownersLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L10n.t("common.loading"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (ownerQuery.isNotBlank() && model.owners.isEmpty()) {
                Text(
                    L10n.t("ticket.assign.empty"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    model.owners.forEach { user ->
                        if (user.id == 1) return@forEach
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { model.setOwner(user.id) }
                                }
                                .padding(vertical = 9.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    user.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val email = user.email
                                if (!email.isNullOrBlank() && email != "-") {
                                    Text(
                                        email,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(7.dp))
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ActionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

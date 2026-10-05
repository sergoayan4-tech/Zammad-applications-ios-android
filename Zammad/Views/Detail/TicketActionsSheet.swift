import SwiftUI

/// Bottom sheet with the main ticket actions: state, priority, group, owner.
struct TicketActionsSheet: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.dismiss) private var dismiss

    let model: TicketDetailModel

    @State private var ownerQuery = ""

    var body: some View {
        NavigationStack {
            List {
                stateSection
                prioritySection
                groupSection
                assignSection
                ownerSearchSection

                if let error = model.errorMessage {
                    Section {
                        Text(prefs.message(for: error))
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .disabled(model.updating)
            .navigationTitle(prefs.t("ticket.actions"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(prefs.t("common.done")) {
                        dismiss()
                    }
                }
            }
        }
        .task {
            await model.loadOwners(api: session.api)
        }
    }

    // MARK: - Sections

    private var stateSection: some View {
        Section(prefs.t("ticket.changeState")) {
            ForEach(session.reference.activeStates) { state in
                Button {
                    Task { await model.setState(id: state.id, api: session.api) }
                } label: {
                    row(title: state.name, selected: model.ticket.stateId == state.id)
                }
            }
        }
    }

    private var prioritySection: some View {
        Section(prefs.t("ticket.changePriority")) {
            ForEach(session.reference.activePriorities) { priority in
                Button {
                    Task { await model.setPriority(id: priority.id, api: session.api) }
                } label: {
                    row(title: priority.name, selected: model.ticket.priorityId == priority.id)
                }
            }
        }
    }

    private var groupSection: some View {
        Section(prefs.t("ticket.changeGroup")) {
            ForEach(session.reference.activeGroups) { group in
                Button {
                    Task { await model.setGroup(id: group.id, api: session.api) }
                } label: {
                    row(title: group.name, selected: model.ticket.groupId == group.id)
                }
            }
        }
    }

    private var assignSection: some View {
        Section(prefs.t("ticket.assign")) {
            Button {
                Task { await assignToMe() }
            } label: {
                row(title: prefs.t("ticket.assign.me"), selected: false)
            }

            Button {
                Task { await model.setOwner(id: 1, api: session.api) }
            } label: {
                row(title: prefs.t("ticket.assign.unassigned"), selected: false)
            }
        }
    }

    private var ownerSearchSection: some View {
        Section {
            TextField(prefs.t("ticket.assign.search"), text: $ownerQuery)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onChange(of: ownerQuery) { _, newValue in
                    model.searchOwners(newValue, api: session.api)
                }

            if model.ownersLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
            } else if model.owners.isEmpty {
                Text(prefs.t("ticket.assign.empty"))
                    .foregroundStyle(.secondary)
            } else {
                ForEach(model.owners) { user in
                    Button {
                        Task { await model.setOwner(id: user.id, api: session.api) }
                    } label: {
                        ownerRow(user)
                    }
                }
            }
        }
    }

    // MARK: - Rows

    private func row(title: String, selected: Bool) -> some View {
        HStack(spacing: 8) {
            Text(title)
                .foregroundStyle(selected ? Color.accentColor : Color.primary)
            Spacer()
            if selected {
                Image(systemName: "checkmark")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.accentColor)
            }
        }
        .contentShape(Rectangle())
    }

    private func ownerRow(_ user: ZammadUser) -> some View {
        let selected = model.ticket.ownerId == user.id
        let subtitle = user.subtitle

        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(user.displayName)
                    .foregroundStyle(selected ? Color.accentColor : Color.primary)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if selected {
                Image(systemName: "checkmark")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.accentColor)
            }
        }
        .contentShape(Rectangle())
    }

    // MARK: - Actions

    private func assignToMe() async {
        guard let me = session.currentUser else { return }
        await model.setOwner(id: me.id, api: session.api)
    }
}

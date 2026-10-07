import SwiftUI

/// Create a new ticket.
struct NewTicketView: View {
    @Environment(SessionStore.self) private var session
    @Environment(AppPreferences.self) private var prefs

    @State private var model = NewTicketModel()
    @State private var validationKey: String?

    let onCreated: (Ticket) -> Void

    var body: some View {
        Form {
            Section(prefs.t("new.subject")) {
                TextField(prefs.t("new.subject.placeholder"), text: $model.title)
            }

            Section(prefs.t("new.customer")) {
                TextField(prefs.t("new.customer.placeholder"), text: $model.customerEmail)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            Section {
                Picker(prefs.t("new.group"), selection: $model.groupId) {
                    Text("—").tag(Int?.none)
                    ForEach(session.reference.activeGroups) { group in
                        Text(group.name).tag(Optional(group.id))
                    }
                }

                Picker(prefs.t("new.priority"), selection: $model.priorityId) {
                    Text("—").tag(Int?.none)
                    ForEach(session.reference.activePriorities) { priority in
                        Text(priority.name).tag(Optional(priority.id))
                    }
                }

                Picker(prefs.t("new.state"), selection: $model.stateId) {
                    Text("—").tag(Int?.none)
                    ForEach(session.reference.activeStates) { state in
                        Text(state.name).tag(Optional(state.id))
                    }
                }
            }

            Section(prefs.t("ticket.owner")) {
                Text(ownerLabel)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    Button {
                        model.ownerId = nil
                        model.ownerName = nil
                    } label: {
                        Text(prefs.t("ticket.assign.me"))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(model.ownerId == nil ? .accentColor : nil)

                    Button {
                        model.ownerId = 1
                        model.ownerName = nil
                    } label: {
                        Text(prefs.t("ticket.assign.unassigned"))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(model.ownerId == 1 ? .accentColor : nil)
                }

                TextField(prefs.t("ticket.assign.search"), text: $model.ownerQuery)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onChange(of: model.ownerQuery) { _, newValue in
                        model.searchOwners(newValue, api: session.api)
                    }

                if model.ownersLoading {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(prefs.t("common.loading"))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else if !model.ownerQuery.trimmed.isEmpty && model.owners.isEmpty {
                    Text(prefs.t("ticket.assign.empty"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                ForEach(model.owners) { user in
                    Button {
                        model.selectOwner(user)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.displayName)
                            if !user.subtitle.isEmpty {
                                Text(user.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                }
            }

            Section(prefs.t("new.message")) {
                TextField(prefs.t("new.message.placeholder"), text: $model.body, axis: .vertical)
                    .lineLimit(4...10)
            }

            if let validationKey {
                Section {
                    Text(prefs.t(validationKey))
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }

            if let error = model.errorMessage {
                Section {
                    Text(prefs.message(for: error))
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }

            Section {
                Button {
                    Task { await submit() }
                } label: {
                    Group {
                        if model.creating {
                            HStack(spacing: 10) {
                                ProgressView()
                                Text(prefs.t("new.creating"))
                            }
                        } else {
                            Text(prefs.t("new.submit"))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(model.creating)
            }
        }
        .navigationTitle(prefs.t("new.title"))
        .task {
            model.applyDefaults(from: session.reference)
        }
    }

    private var ownerLabel: String {
        if let ownerId = model.ownerId {
            if ownerId == 1 {
                return prefs.t("ticket.assign.unassigned")
            }
            return model.ownerName ?? "#\(ownerId)"
        }
        let me = session.currentUser?.displayName ?? ""
        return prefs.t("ticket.assign.me") + (me.isEmpty ? "" : ": \(me)")
    }

    private func submit() async {
        validationKey = nil

        if model.title.trimmed.isEmpty {
            validationKey = "new.error.title"
            return
        }
        if model.groupId == nil && !session.reference.activeGroups.isEmpty {
            validationKey = "new.error.group"
            return
        }
        if model.body.trimmed.isEmpty {
            validationKey = "new.error.message"
            return
        }

        if let ticket = await model.create(api: session.api) {
            onCreated(ticket)
        }
    }
}

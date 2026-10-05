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

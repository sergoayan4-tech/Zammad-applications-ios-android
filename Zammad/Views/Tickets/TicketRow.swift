import SwiftUI

/// One row in the ticket list.
struct TicketRow: View {
    let ticket: Ticket
    let reference: ReferenceData
    let prefs: AppPreferences

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(ticket.displayNumber)
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer(minLength: 4)
                if !stateName.isEmpty {
                    StateChip(text: stateName)
                }
            }

            Text(ticket.displayTitle)
                .font(.subheadline.weight(.semibold))
                .lineLimit(2)

            HStack(spacing: 10) {
                if !groupName.isEmpty {
                    Label(groupName, systemImage: "folder")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Text(ownerName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer(minLength: 4)

                Text(prefs.relativeDate(ticket.updatedAt ?? ticket.createdAt))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var stateName: String {
        ticketStateName(ticket, reference)
    }

    private var groupName: String {
        ticketGroupName(ticket, reference)
    }

    private var ownerName: String {
        ticketOwnerName(ticket)
    }
}

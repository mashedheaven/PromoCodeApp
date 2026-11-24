import PromoCodeCore
import SwiftUI

struct MembershipListView: View {
    @Environment(AppModel.self) private var model
    @State private var editing: MembershipTarget?

    var body: some View {
        @Bindable var router = model.router
        let today = model.today
        let all = model.memberships.sorted { a, b in
            if a.isActive != b.isActive { return a.isActive }
            return (a.nextRenewal(today)?.epochDay ?? .max) < (b.nextRenewal(today)?.epochDay ?? .max)
        }
        let yearly = Dictionary(grouping: all.filter { $0.isActive && $0.annualCost > 0 }, by: \.currency)
            .mapValues { $0.reduce(0) { $0 + $1.annualCost } }

        NavigationStack {
            List {
                if !yearly.isEmpty {
                    Section {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("You spend per year").font(.subheadline).foregroundStyle(.secondary)
                            Text(yearly.sorted { $0.key < $1.key }.map { Money.format($0.value, currency: $0.key) }.joined(separator: " + "))
                                .font(.title2.bold())
                        }
                    }
                }
                ForEach(all) { m in
                    Button { editing = MembershipTarget(id: m.id) } label: { MembershipRowView(membership: m, today: today) }
                        .tint(.primary)
                }
            }
            .overlay {
                if all.isEmpty {
                    EmptyStateView(systemImage: "person.text.rectangle", title: "No memberships yet",
                                   message: "Track gym memberships, subscriptions and loyalty cards, and get reminded before they renew.")
                }
            }
            .navigationTitle("Memberships")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { editing = MembershipTarget(id: nil) } label: { Label("Add membership", systemImage: "plus") }
                }
            }
            .sheet(item: $editing) { MembershipEditView(membershipId: $0.id) }
            .onChange(of: router.editingMembership, initial: true) { _, id in
                // Opened from a reminder notification.
                if let id {
                    editing = MembershipTarget(id: id)
                    router.editingMembership = nil
                }
            }
        }
    }
}

struct MembershipTarget: Identifiable, Hashable { let id: String? }

private struct MembershipRowView: View {
    let membership: Membership
    let today: Day

    var body: some View {
        let m = membership
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(m.organization).font(.headline).foregroundStyle(.primary)
                Spacer()
                Text(m.type.label).font(.caption).foregroundStyle(.secondary)
            }
            if !m.memberNumber.isEmpty {
                Text("No. \(m.memberNumber)").font(.subheadline.monospaced()).foregroundStyle(.secondary)
            }
            HStack(spacing: 6) {
                if !m.isActive {
                    Text("Inactive").foregroundStyle(.secondary)
                } else if let next = m.nextRenewal(today) {
                    let expiry = Expiry.of(next, today: today)
                    Text(renewalText(expiry, next)).foregroundStyle(expiry.color)
                }
                if let fee = m.fee, fee > 0, m.billingCycle != .NONE {
                    Text("\(Money.format(fee, currency: m.currency)) \(m.billingCycle.label.lowercased())").foregroundStyle(.secondary)
                }
            }
            .font(.caption.weight(.medium))
        }
        .accessibilityElement(children: .combine)
    }

    private func renewalText(_ expiry: Expiry, _ next: Day) -> String {
        switch expiry {
        case .expired: return "Ended \(next.mediumText)"
        case .today: return "Renews today"
        case .inDays(1): return "Renews tomorrow"
        case .inDays(let d): return "Renews in \(d) days"
        case .never: return ""
        }
    }
}

struct MembershipEditView: View {
    let membershipId: String?
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var loaded = false
    @State private var original: Membership?
    @State private var organization = ""
    @State private var memberNumber = ""
    @State private var type: MembershipType = .OTHER
    @State private var billing: BillingCycle = .NONE
    @State private var fee = ""
    @State private var hasRenewal = false
    @State private var renewsOn = Date()
    @State private var reminderDays = 7
    @State private var notes = ""
    @State private var isActive = true
    @State private var placeIds: [String] = []
    @State private var showErrors = false
    /// A second tap on Save while the sheet closes must not create a duplicate.
    @State private var saved = false
    @State private var scanning = false
    @State private var pickingPlaces = false
    @State private var confirmDelete = false

    private var feeError: String? { billing != .NONE && !fee.isEmpty && Money.parse(fee) == nil ? "Enter an amount" : nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Organization", text: $organization).textInputAutocapitalization(.words)
                    if showErrors && organization.trimmingCharacters(in: .whitespaces).isEmpty { ErrorText("Enter the organization") }
                    Picker("Type", selection: $type) { ForEach(MembershipType.allCases, id: \.self) { Text($0.label).tag($0) } }
                    HStack {
                        TextField("Member number (optional)", text: $memberNumber)
                            .font(.body.monospaced())
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                        Button { scanning = true } label: { Image(systemName: "barcode.viewfinder") }.accessibilityLabel("Scan card")
                    }
                }
                Section("Billing") {
                    Picker("Billing", selection: $billing) { ForEach(BillingCycle.allCases, id: \.self) { Text($0.label).tag($0) } }
                    if billing != .NONE {
                        TextField("Fee", text: $fee).keyboardType(.decimalPad)
                        if let e = feeError { ErrorText(e) }
                    }
                    Toggle(billing == .NONE ? "Has an end date" : "Next renewal", isOn: $hasRenewal)
                    if hasRenewal {
                        DatePicker(billing == .NONE ? "Valid until" : "Renews on", selection: $renewsOn, displayedComponents: .date)
                        // Only meaningful when there's a date to remind about.
                        Picker("Remind me", selection: $reminderDays) {
                            ForEach([0, 1, 3, 7, 14, 30], id: \.self) { d in
                                Text(d == 0 ? "Never" : d == 1 ? "1 day before" : "\(d) days before").tag(d)
                            }
                        }
                    }
                    Toggle("Active", isOn: $isActive)
                }
                Section {
                    ForEach(model.places.filter { placeIds.contains($0.id) }) { Label($0.name, systemImage: "mappin") }
                        .onDelete { offsets in
                            let shown = model.places.filter { placeIds.contains($0.id) }
                            let removed = Set(offsets.map { shown[$0].id })
                            placeIds.removeAll { removed.contains($0) }
                        }
                    Button { pickingPlaces = true } label: { Label("Add place", systemImage: "plus") }
                } header: {
                    Text("Locations")
                } footer: {
                    Text("Get a reminder to use this membership when you're nearby.")
                }
                Section("Benefits and notes") {
                    TextField("Optional", text: $notes, axis: .vertical).lineLimit(2...6)
                }
                if original != nil {
                    Section {
                        Button("Delete membership", role: .destructive) { confirmDelete = true }
                            .confirmationDialog("Delete membership?", isPresented: $confirmDelete, titleVisibility: .visible) {
                                Button("Delete", role: .destructive) {
                                    if let id = original?.id { model.library.deleteMembership(id) }
                                    dismiss()
                                }
                            } message: {
                                Text("\(organization) will be removed from all your devices.")
                            }
                    }
                }
            }
            .keyboardDismissable()
            .navigationTitle(original == nil ? "New membership" : "Edit membership")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
            .sheet(isPresented: $scanning) { ScannerSheet { memberNumber = $0.value } }
            .sheet(isPresented: $pickingPlaces) { PlacePicker(selected: $placeIds, suggestedName: organization) }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard let id = membershipId, let m = model.membership(id) else { return }
        original = m
        organization = m.organization
        memberNumber = m.memberNumber
        type = m.type
        billing = m.billingCycle
        fee = m.fee.map { $0.truncatingRemainder(dividingBy: 1) == 0 ? String(Int($0)) : String($0) } ?? ""
        hasRenewal = m.renewsOn != nil
        renewsOn = m.renewsOn?.noonDate ?? Date()
        reminderDays = m.reminderDays
        notes = m.notes ?? ""
        isActive = m.isActive
        placeIds = m.placeIds
    }

    private func save() {
        guard !saved else { return }
        showErrors = true
        let org = organization.trimmingCharacters(in: .whitespaces)
        guard !org.isEmpty, feeError == nil else { return }
        var m = original ?? Membership(organization: org, startedOn: model.today, currency: Money.defaultCurrency())
        m.organization = org
        m.memberNumber = memberNumber.trimmingCharacters(in: .whitespaces)
        m.type = type
        m.billingCycle = billing
        m.fee = billing == .NONE ? nil : Money.parse(fee)
        m.renewsOn = hasRenewal ? Day(date: renewsOn) : nil
        m.reminderDays = reminderDays
        m.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes.trimmingCharacters(in: .whitespacesAndNewlines)
        m.isActive = isActive
        m.placeIds = placeIds
        saved = true
        model.library.save(m)
        dismiss()
    }
}

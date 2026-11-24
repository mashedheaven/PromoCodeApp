import MapKit
import PromoCodeCore
import SwiftUI
import UIKit

// MARK: - List

struct CouponListView: View {
    @Environment(AppModel.self) private var model
    @State private var search = ""
    @State private var sort: CouponSort = .expiry
    @State private var editing: CouponEditTarget?
    @State private var alertsReady = true

    var body: some View {
        @Bindable var router = model.router
        let today = model.today
        let query = CouponQuery(text: search, filter: router.couponFilter, sort: sort, placeId: router.placeFilter)
        let rows = query.apply(model.coupons, today: today)
        let placeNames = Dictionary(model.places.map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })

        NavigationStack(path: $router.couponPath) {
            List {
                Section {
                    filterBar(placeName: router.placeFilter.flatMap { placeNames[$0] })
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    if !alertsReady && !model.places.isEmpty {
                        AlertsBanner { router.tab = .settings }
                    }
                }
                ForEach(rows) { coupon in
                    NavigationLink(value: CouponRoute.detail(coupon.id)) {
                        CouponRow(coupon: coupon, today: today, placeNames: coupon.placeIds.compactMap { placeNames[$0] })
                    }
                    .swipeActions(edge: .leading) {
                        Button { model.library.setFavorite(coupon.id, !coupon.isFavorite) } label: {
                            Label(coupon.isFavorite ? "Unfavorite" : "Favorite", systemImage: coupon.isFavorite ? "heart.slash" : "heart")
                        }
                        .tint(.pink)
                    }
                    .swipeActions(edge: .trailing) {
                        Button { model.library.setUsed(coupon.id, !coupon.isUsed) } label: {
                            Label(coupon.isUsed ? "Unused" : "Used", systemImage: "checkmark.circle")
                        }
                        .tint(.green)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .overlay {
                if rows.isEmpty {
                    if model.coupons.isEmpty {
                        EmptyStateView(systemImage: "tag", title: "No coupons yet",
                                       message: "Add a promo code and the stores where it works. You'll get a reminder when you're nearby.")
                    } else if !search.trimmingCharacters(in: .whitespaces).isEmpty {
                        EmptyStateView(systemImage: "magnifyingglass", title: "No results", message: "No coupons match \"\(search)\".")
                    } else {
                        EmptyStateView(systemImage: "tray", title: "Nothing here", message: router.couponFilter.emptyMessage)
                    }
                }
            }
            .searchable(text: $search, prompt: "Store, code or notes")
            .navigationTitle("Coupons")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        Picker("Sort", selection: $sort) {
                            ForEach(CouponSort.allCases, id: \.self) { Text($0.label).tag($0) }
                        }
                    } label: { Label("Sort", systemImage: "arrow.up.arrow.down") }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { editing = .new } label: { Label("Add coupon", systemImage: "plus") }
                }
            }
            .navigationDestination(for: CouponRoute.self) { route in
                switch route {
                case .detail(let id): CouponDetailView(couponId: id)
                }
            }
            .sheet(item: $editing) { target in CouponEditView(target: target) }
            .task(id: model.permissionsRevision) { alertsReady = await alertsAreReady() }
        }
    }

    private func filterBar(placeName: String?) -> some View {
        let router = model.router
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if let placeName {
                    Chip(title: placeName, selected: true, systemImage: "xmark.circle.fill") { router.placeFilter = nil }
                        .accessibilityHint("Show coupons for all places")
                }
                ForEach(CouponFilter.allCases, id: \.self) { filter in
                    Chip(title: filter.label, selected: router.couponFilter == filter) { router.couponFilter = filter }
                }
            }
            .padding(.vertical, 4)
        }
    }

    private func alertsAreReady() async -> Bool {
        let notifications = await model.notifications.status()
        return model.regions.canMonitor && (notifications == .authorized || notifications == .provisional)
    }
}

enum CouponEditTarget: Identifiable, Hashable {
    case new
    case existing(String)
    var id: String { if case .existing(let id) = self { return id } else { return "new" } }
}

private struct AlertsBanner: View {
    let onSetUp: () -> Void
    var body: some View {
        HStack {
            Image(systemName: "bell.badge")
            Text("Nearby alerts are off").font(.subheadline)
            Spacer()
            Button("Turn on", action: onSetUp).font(.subheadline.weight(.semibold))
        }
    }
}

struct CouponRow: View {
    let coupon: Coupon
    let today: Day
    let placeNames: [String]

    var body: some View {
        let expiry = coupon.expiry(today)
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(coupon.merchant).font(.headline).lineLimit(1)
                if coupon.isFavorite { Image(systemName: "heart.fill").foregroundStyle(.pink).font(.caption) }
                Spacer()
                if let category = coupon.category { Text(category).font(.caption).foregroundStyle(.secondary) }
            }
            HStack(spacing: 10) {
                Text(coupon.discount.label())
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.accentColor.opacity(0.18), in: RoundedRectangle(cornerRadius: 6))
                Text(coupon.code).font(.body.monospaced()).lineLimit(1)
            }
            HStack(spacing: 6) {
                if coupon.isUsed {
                    Text("Used").foregroundStyle(.secondary)
                } else if coupon.isArchived {
                    Text("Archived").foregroundStyle(.secondary)
                } else {
                    Text(expiry.text).foregroundStyle(expiry.color)
                }
                if !placeNames.isEmpty {
                    Label(placeNames.joined(separator: ", "), systemImage: "mappin").foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .font(.caption.weight(.medium))
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Detail

struct CouponDetailView: View {
    let couponId: String
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var editing: CouponEditTarget?
    @State private var confirmDelete = false
    @State private var copied = false

    var body: some View {
        if let coupon = model.coupon(couponId) {
            content(coupon)
        } else {
            // Deleted here or on another device.
            EmptyStateView(systemImage: "tag.slash", title: "Coupon removed", message: "This coupon no longer exists.")
        }
    }

    private func content(_ coupon: Coupon) -> some View {
        let today = model.today
        let expiry = coupon.expiry(today)
        let places = model.places.filter { coupon.placeIds.contains($0.id) }
        return List {
            Section {
                Text(coupon.discount.label()).font(.largeTitle.bold()).foregroundStyle(Color.accentColor)
                if !coupon.code.isEmpty {
                    HStack {
                        VStack(alignment: .leading) {
                            Text("Code").font(.caption).foregroundStyle(.secondary)
                            Text(coupon.code).font(.title2.monospaced()).textSelection(.enabled)
                        }
                        Spacer()
                        Button {
                            UIPasteboard.general.string = coupon.code
                            copied = true
                        } label: {
                            Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                        }
                        .buttonStyle(.bordered)
                    }
                }
                if let barcode = coupon.barcode, let format = coupon.barcodeFormat {
                    BarcodeView(content: barcode, format: format)
                        .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                }
            }
            Section {
                LabeledContent("Expires") {
                    Text(coupon.expiresOn.map { "\($0.mediumText) · \(expiry.text)" } ?? "No expiry date").foregroundStyle(expiry.color)
                }
                if let min = coupon.minPurchase {
                    LabeledContent("Minimum purchase", value: Money.format(min, currency: currency(coupon)))
                }
                if let category = coupon.category { LabeledContent("Category", value: category) }
                if coupon.isUsed { LabeledContent("Status", value: "Used") }
                if coupon.isArchived { LabeledContent("Status", value: "Archived") }
                if let notes = coupon.notes { Text(notes) }
            }
            if !places.isEmpty {
                Section("Use it at") {
                    ForEach(places) { place in
                        Button { openInMaps(place) } label: { Label(place.name, systemImage: "mappin.circle") }
                    }
                }
            }
            Section {
                Button(coupon.isUsed ? "Mark as unused" : "Mark as used") { model.library.setUsed(coupon.id, !coupon.isUsed) }
                Button(coupon.isArchived ? "Unarchive" : "Archive") { model.library.setArchived(coupon.id, !coupon.isArchived) }
                Button("Delete coupon", role: .destructive) { confirmDelete = true }
            }
        }
        .navigationTitle(coupon.merchant)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button { model.library.setFavorite(coupon.id, !coupon.isFavorite) } label: {
                    Label(coupon.isFavorite ? "Remove from favorites" : "Add to favorites", systemImage: coupon.isFavorite ? "heart.fill" : "heart")
                }
                ShareLink(item: shareText(coupon)) { Label("Share", systemImage: "square.and.arrow.up") }
                Button("Edit") { editing = .existing(coupon.id) }
            }
        }
        .sheet(item: $editing) { CouponEditView(target: $0) }
        .confirmationDialog("Delete coupon?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                model.library.deleteCoupon(coupon.id)
                dismiss()
            }
        } message: {
            Text("The \(coupon.merchant) coupon will be removed from all your devices.")
        }
    }

    private func currency(_ c: Coupon) -> String {
        if case .fixedAmount(_, let cur) = c.discount { return cur }
        return Money.defaultCurrency()
    }

    private func shareText(_ c: Coupon) -> String {
        var text = "\(c.merchant): \(c.discount.label())"
        if !c.code.isEmpty { text += " with code \(c.code)" }
        if let e = c.expiresOn { text += " (expires \(e.mediumText))" }
        return text
    }

    private func openInMaps(_ place: Place) {
        let item = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: place.latitude, longitude: place.longitude)))
        item.name = place.name
        item.openInMaps()
    }
}

// MARK: - Editor

enum DiscountKind: String, CaseIterable, Identifiable {
    case percent = "Percent off", amount = "Amount off", bogo = "Buy one, get one", freeShipping = "Free shipping"
    var id: String { rawValue }
    var needsValue: Bool { self == .percent || self == .amount }
}

let couponCategories = ["Groceries", "Restaurants", "Fashion", "Electronics", "Home", "Beauty", "Travel", "Entertainment", "Other"]

struct CouponEditView: View {
    let target: CouponEditTarget
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var loaded = false
    @State private var original: Coupon?
    @State private var merchant = ""
    @State private var code = ""
    @State private var kind: DiscountKind = .percent
    @State private var value = ""
    @State private var hasExpiry = false
    @State private var expiresOn = Date()
    @State private var minPurchase = ""
    @State private var category: String?
    @State private var notes = ""
    @State private var barcode: ScannedCode?
    @State private var placeIds: [String] = []
    @State private var showErrors = false
    @State private var scanning = false
    @State private var pickingPlaces = false

    private var merchantError: String? { merchant.trimmingCharacters(in: .whitespaces).isEmpty ? "Enter the store name" : nil }
    private var valueError: String? {
        guard kind.needsValue else { return nil }
        guard let v = Money.parse(value), v > 0 else { return "Enter the discount" }
        return kind == .percent && v > 100 ? "Can't be more than 100%" : nil
    }
    private var minPurchaseError: String? { !minPurchase.isEmpty && Money.parse(minPurchase) == nil ? "Enter an amount" : nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Store", text: $merchant).textInputAutocapitalization(.words)
                    if showErrors, let e = merchantError { ErrorText(e) }
                    HStack {
                        TextField("Promo code", text: $code)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .font(.body.monospaced())
                        Button { scanning = true } label: { Image(systemName: "barcode.viewfinder") }
                            .accessibilityLabel("Scan barcode")
                    }
                    if barcode != nil && barcode?.value == code.trimmingCharacters(in: .whitespaces) {
                        Text("Scanned barcode saved").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Section("Discount") {
                    Picker("Type", selection: $kind) { ForEach(DiscountKind.allCases) { Text($0.rawValue).tag($0) } }
                    if kind.needsValue {
                        HStack {
                            TextField(kind == .percent ? "Percent" : "Amount", text: $value).keyboardType(.decimalPad)
                            if kind == .percent { Text("%").foregroundStyle(.secondary) }
                        }
                        if showErrors, let e = valueError { ErrorText(e) }
                    }
                    TextField("Minimum purchase (optional)", text: $minPurchase).keyboardType(.decimalPad)
                    if let e = minPurchaseError { ErrorText(e) }
                }
                Section {
                    Toggle("Expires", isOn: $hasExpiry)
                    if hasExpiry { DatePicker("Expiry date", selection: $expiresOn, displayedComponents: .date) }
                    Picker("Category", selection: $category) {
                        Text("None").tag(String?.none)
                        ForEach(couponCategories, id: \.self) { Text($0).tag(String?.some($0)) }
                    }
                }
                Section {
                    ForEach(model.places.filter { placeIds.contains($0.id) }) { place in
                        Label(place.name, systemImage: "mappin")
                    }
                    .onDelete { offsets in
                        let shown = model.places.filter { placeIds.contains($0.id) }
                        let removed = Set(offsets.map { shown[$0].id })
                        placeIds.removeAll { removed.contains($0) }
                    }
                    Button { pickingPlaces = true } label: { Label("Add place", systemImage: "plus") }
                } header: {
                    Text("Where can you use it?")
                } footer: {
                    Text("You'll get a notification when you're near one of these places.")
                }
                Section("Notes") {
                    TextField("Notes (optional)", text: $notes, axis: .vertical).lineLimit(2...6)
                }
            }
            .navigationTitle(target == .new ? "New coupon" : "Edit coupon")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
            .sheet(isPresented: $scanning) {
                ScannerSheet { scan in
                    let format = scan.format
                    let value = format?.normalized(scan.value) ?? scan.value
                    code = value
                    barcode = ScannedCode(value: value, format: format)
                }
            }
            .sheet(isPresented: $pickingPlaces) {
                PlacePicker(selected: $placeIds, suggestedName: merchant)
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard case .existing(let id) = target, let c = model.coupon(id) else { return }
        original = c
        merchant = c.merchant
        code = c.code
        switch c.discount {
        case .percentage(let p): kind = .percent; value = Self.number(p)
        case .fixedAmount(let a, _): kind = .amount; value = Self.number(a)
        case .buyOneGetOne: kind = .bogo
        case .freeShipping: kind = .freeShipping
        }
        minPurchase = c.minPurchase.map(Self.number) ?? ""
        category = c.category
        notes = c.notes ?? ""
        hasExpiry = c.expiresOn != nil
        expiresOn = c.expiresOn?.noonDate ?? Date()
        if let b = c.barcode { barcode = ScannedCode(value: b, format: c.barcodeFormat) }
        placeIds = c.placeIds
    }

    private func save() {
        showErrors = true
        guard merchantError == nil, valueError == nil, minPurchaseError == nil else { return }
        let amount = Money.parse(value) ?? 0
        let currency: String = {
            if case .fixedAmount(_, let cur) = original?.discount { return cur }
            return Money.defaultCurrency()
        }()
        let trimmedCode = code.trimmingCharacters(in: .whitespaces)
        var c = original ?? Coupon(code: "", merchant: "", discount: .freeShipping)
        c.merchant = merchant.trimmingCharacters(in: .whitespaces)
        c.code = trimmedCode
        switch kind {
        case .percent: c.discount = .percentage(amount)
        case .amount: c.discount = .fixedAmount(amount, currency: currency)
        case .bogo: c.discount = .buyOneGetOne
        case .freeShipping: c.discount = .freeShipping
        }
        c.minPurchase = Money.parse(minPurchase)
        c.category = category
        c.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes.trimmingCharacters(in: .whitespacesAndNewlines)
        c.expiresOn = hasExpiry ? Day(date: expiresOn) : nil
        // A scanned barcode stops being valid once the code is retyped.
        let keep = barcode?.value == trimmedCode
        c.barcode = keep ? barcode?.value : nil
        c.barcodeFormat = keep ? barcode?.format : nil
        c.placeIds = placeIds
        model.library.save(c)
        dismiss()
    }

    private static func number(_ v: Double) -> String {
        v.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(v)) : String(v)
    }
}

struct ErrorText: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.caption).foregroundStyle(.red) }
}

/// Multi-select list of saved places, with a shortcut to create one.
struct PlacePicker: View {
    @Binding var selected: [String]
    let suggestedName: String
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var creating = false

    var body: some View {
        NavigationStack {
            List {
                if model.places.isEmpty {
                    Text("You haven't saved any places yet. Add a store location to get an alert when you're nearby.")
                        .foregroundStyle(.secondary)
                }
                ForEach(model.places) { place in
                    Button {
                        if let i = selected.firstIndex(of: place.id) { selected.remove(at: i) } else { selected.append(place.id) }
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(place.name).foregroundStyle(.primary)
                                if let address = place.address { Text(address).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                            }
                            Spacer()
                            if selected.contains(place.id) { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
                        }
                    }
                }
                Button { creating = true } label: { Label("New place", systemImage: "plus") }
            }
            .navigationTitle("Where can you use it?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .sheet(isPresented: $creating) {
                PlaceEditView(placeId: nil, suggestedName: suggestedName) { newId in selected.append(newId) }
            }
        }
    }
}

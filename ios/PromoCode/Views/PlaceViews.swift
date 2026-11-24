import CoreLocation
import MapKit
import PromoCodeCore
import SwiftUI

struct PlaceListView: View {
    @Environment(AppModel.self) private var model
    @State private var editing: PlaceTarget?

    var body: some View {
        let today = model.today
        let active = model.coupons.filter { $0.isRedeemable(today) }
        let memberships = model.memberships.filter(\.isActive)
        NavigationStack {
            List(model.places) { place in
                Button { editing = PlaceTarget(id: place.id) } label: {
                    HStack(alignment: .top) {
                        Image(systemName: "mappin.circle.fill").foregroundStyle(Color.accentColor).font(.title2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(place.name).foregroundStyle(.primary)
                            if let address = place.address { Text(address).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                            Text(summary(coupons: active.filter { $0.placeIds.contains(place.id) }.count,
                                         memberships: memberships.filter { $0.placeIds.contains(place.id) }.count))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(place.radiusMeters) m").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .overlay {
                if model.places.isEmpty {
                    EmptyStateView(systemImage: "mappin.and.ellipse", title: "No places yet",
                                   message: "Save the stores you visit, then attach coupons to them to be reminded when you're nearby.")
                }
            }
            .navigationTitle("Places")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { editing = PlaceTarget(id: nil) } label: { Label("Add place", systemImage: "plus") }
                }
            }
            .sheet(item: $editing) { target in PlaceEditView(placeId: target.id, suggestedName: "") { _ in } }
        }
    }

    private func summary(coupons: Int, memberships: Int) -> String {
        var parts: [String] = []
        if coupons > 0 { parts.append(coupons == 1 ? "1 coupon" : "\(coupons) coupons") }
        if memberships > 0 { parts.append(memberships == 1 ? "1 membership" : "\(memberships) memberships") }
        return parts.isEmpty ? "Nothing attached, so no alerts" : parts.joined(separator: " · ")
    }
}

struct PlaceTarget: Identifiable {
    let id: String?
    var identity: String { id ?? "new" }
}

extension PlaceTarget: Hashable {}

struct PlaceEditView: View {
    let placeId: String?
    let suggestedName: String
    let onSaved: (String) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var loaded = false
    @State private var original: Place?
    @State private var name = ""
    @State private var address = ""
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var radius = Double(Place.defaultRadius)
    @State private var results: [MKMapItem] = []
    @State private var busy = false
    @State private var message: String?
    @State private var showErrors = false
    @State private var confirmDelete = false
    @State private var camera: MapCameraPosition = .automatic

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name, e.g. Target – Downtown", text: $name).textInputAutocapitalization(.words)
                    if showErrors && name.trimmingCharacters(in: .whitespaces).isEmpty { ErrorText("Give this place a name") }
                }
                Section("Location") {
                    HStack {
                        TextField("Search for an address or store", text: $address)
                            .textInputAutocapitalization(.words)
                            .submitLabel(.search)
                            .onSubmit(search)
                        Button(action: search) { Image(systemName: "magnifyingglass") }.accessibilityLabel("Find address")
                    }
                    ForEach(results, id: \.self) { item in
                        Button { choose(item) } label: {
                            VStack(alignment: .leading) {
                                Text(item.name ?? "Result").foregroundStyle(.primary)
                                Text(item.placemark.title ?? "").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    Button(action: useCurrentLocation) {
                        HStack {
                            Label("Use my current location", systemImage: "location")
                            if busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(busy)
                    if let message { Text(message).font(.caption).foregroundStyle(.red) }
                    map
                        .frame(height: 260)
                        .listRowInsets(EdgeInsets())
                }
                Section {
                    VStack(alignment: .leading) {
                        Text("Alert radius: \(Int(radius)) m")
                        Slider(value: $radius, in: Double(Place.minRadius)...1000, step: 50)
                    }
                } footer: {
                    Text("A larger radius is more reliable but alerts you earlier. 150 m suits most stores.")
                }
                if original != nil {
                    Section { Button("Delete place", role: .destructive) { confirmDelete = true } }
                }
            }
            .navigationTitle(original == nil ? "New place" : "Edit place")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
            .confirmationDialog("Delete place?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let id = original?.id { model.library.deletePlace(id) }
                    dismiss()
                }
            } message: {
                Text("Coupons and memberships linked to \(name) are kept, but you'll no longer be alerted here.")
            }
            .onAppear(perform: load)
        }
    }

    /// Tap to pin; the circle previews the alert radius.
    private var map: some View {
        MapReader { proxy in
            Map(position: $camera) {
                if let coordinate {
                    Marker(name.isEmpty ? "Place" : name, coordinate: coordinate)
                    MapCircle(center: coordinate, radius: radius)
                        .foregroundStyle(Color.accentColor.opacity(0.2))
                        .stroke(Color.accentColor, lineWidth: 2)
                }
                UserAnnotation()
            }
            .onTapGesture { point in
                if let tapped = proxy.convert(point, from: .local) { pin(tapped, lookUpAddress: true) }
            }
        }
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        radius = Double(model.settings.defaultRadiusMeters)
        name = suggestedName
        guard let id = placeId, let p = model.place(id) else { return }
        original = p
        name = p.name
        address = p.address ?? ""
        radius = Double(p.radiusMeters)
        pin(CLLocationCoordinate2D(latitude: p.latitude, longitude: p.longitude), lookUpAddress: false)
    }

    private func pin(_ c: CLLocationCoordinate2D, lookUpAddress: Bool) {
        coordinate = c
        results = []
        message = nil
        camera = .region(MKCoordinateRegion(center: c, latitudinalMeters: max(radius * 4, 600), longitudinalMeters: max(radius * 4, 600)))
        guard lookUpAddress else { return }
        Task {
            let placemark = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: c.latitude, longitude: c.longitude)).first
            if let placemark, coordinate?.latitude == c.latitude {
                address = [placemark.name, placemark.locality].compactMap { $0 }.joined(separator: ", ")
            }
        }
    }

    private func search() {
        let query = address.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return }
        busy = true
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        if let c = coordinate {
            request.region = MKCoordinateRegion(center: c, latitudinalMeters: 50_000, longitudinalMeters: 50_000)
        }
        Task {
            let items = (try? await MKLocalSearch(request: request).start().mapItems) ?? []
            busy = false
            results = Array(items.prefix(5))
            if items.isEmpty { message = "No match found. Check the address or your connection." }
            if items.count == 1 { choose(items[0]) }
        }
    }

    private func choose(_ item: MKMapItem) {
        if name.trimmingCharacters(in: .whitespaces).isEmpty, let n = item.name { name = n }
        address = item.placemark.title ?? item.name ?? address
        pin(item.placemark.coordinate, lookUpAddress: false)
    }

    private func useCurrentLocation() {
        let status = model.regions.authorization
        if status == .notDetermined {
            model.regions.requestWhenInUse()
            message = "Allow location access, then tap again."
            return
        }
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            message = "Location access is off. You can turn it on in Settings, or search for the address."
            return
        }
        busy = true
        Task {
            let location = await model.regions.currentLocation()
            busy = false
            if let location { pin(location.coordinate, lookUpAddress: true) } else { message = "Couldn't get your location. Try again outdoors or search for the address." }
        }
    }

    private func save() {
        showErrors = true
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        guard let c = coordinate, Place.isValid(latitude: c.latitude, longitude: c.longitude) else {
            message = "Set the location first: search for an address, use your current location, or tap the map."
            return
        }
        var place = original ?? Place(name: trimmed, latitude: c.latitude, longitude: c.longitude)
        place.name = trimmed
        place.address = address.trimmingCharacters(in: .whitespaces).isEmpty ? nil : address.trimmingCharacters(in: .whitespaces)
        place.latitude = c.latitude
        place.longitude = c.longitude
        place.radiusMeters = Int(radius)
        model.library.save(place)
        onSaved(place.id)
        dismiss()
    }
}

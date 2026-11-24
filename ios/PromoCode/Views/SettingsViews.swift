import CoreLocation
import PromoCodeCore
import SwiftUI
import UIKit
import UserNotifications

struct SettingsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                AlertSetupSection()
                Section("Notifications") {
                    Toggle(isOn: $model.settings.nearbyAlerts) {
                        SettingLabel("Nearby alerts", "When you arrive somewhere you have a coupon or membership")
                    }
                    Toggle(isOn: $model.settings.expiryReminders) {
                        SettingLabel("Expiry reminders", "Before a coupon runs out")
                    }
                    if model.settings.expiryReminders {
                        Picker("Remind me", selection: $model.settings.expiryLeadDays) {
                            ForEach([0, 1, 2, 3, 7], id: \.self) { d in
                                Text(d == 0 ? "On the last day" : d == 1 ? "1 day before" : "\(d) days before").tag(d)
                            }
                        }
                    }
                    Toggle(isOn: $model.settings.membershipReminders) {
                        SettingLabel("Membership reminders", "Before a membership renews")
                    }
                }
                Section("Coupons") {
                    Toggle(isOn: $model.settings.autoArchiveExpired) {
                        SettingLabel("Archive expired coupons", "A week after they expire")
                    }
                    Picker("Default alert radius", selection: $model.settings.defaultRadiusMeters) {
                        ForEach([100, 150, 200, 300, 500, 1000], id: \.self) { Text("\($0) m").tag($0) }
                    }
                }
                Section("Backup & sync") {
                    if model.syncAvailable {
                        NavigationLink {
                            AccountView()
                        } label: {
                            SettingLabel(model.session?.email ?? "Sign in",
                                         model.session != nil ? "Syncing across your devices" : "Back up your coupons and use them on your other devices, including Android")
                        }
                    } else {
                        Text("Your data is stored on this iPhone and included in its iCloud or computer backup. Cloud sync isn't set up in this build.")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }
}

private struct SettingLabel: View {
    let title: String
    let subtitle: String
    init(_ title: String, _ subtitle: String) { self.title = title; self.subtitle = subtitle }
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(subtitle).font(.caption).foregroundStyle(.secondary)
        }
    }
}

/// Walks the user through the permissions nearby alerts depend on.
private struct AlertSetupSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var notifications: UNAuthorizationStatus = .notDetermined

    var body: some View {
        // Location authorization isn't observable; this re-renders when it changes.
        let _ = model.permissionsRevision
        let location = model.regions.authorization
        let precise = model.regions.hasPreciseLocation
        let notificationsOK = notifications == .authorized || notifications == .provisional
        let ready = notificationsOK && location == .authorizedAlways && precise
        Section {
            Label {
                Text(!model.settings.nearbyAlerts ? "Nearby alerts are switched off"
                     : ready ? "Nearby alerts are on" : "Finish setting up nearby alerts")
                    .font(.headline)
            } icon: {
                Image(systemName: ready && model.settings.nearbyAlerts ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                    .foregroundStyle(ready && model.settings.nearbyAlerts ? .green : .orange)
            }
            if !model.settings.nearbyAlerts || ready {
                Text("Your location is checked on this iPhone only and is never uploaded.")
                    .font(.subheadline).foregroundStyle(.secondary)
            } else if !notificationsOK {
                step("Allow notifications so alerts can reach you.", "Allow notifications") {
                    if notifications == .notDetermined {
                        Task { await model.notifications.requestPermission(); await refresh() }
                    } else { openSettings() }
                }
            } else if location == .notDetermined {
                step("Allow location so the app knows when you reach a store.", "Allow location") { model.regions.requestWhenInUse() }
            } else if location == .denied || location == .restricted || !precise {
                step("Location is off or set to approximate. In Settings, choose Location → Always and turn on Precise Location.", "Open Settings", action: openSettings)
            } else {
                step("Last step: choose \"Always\" so alerts work while the app is closed. iOS handles this efficiently and your location never leaves your phone.",
                     model.prefs.askedForAlwaysLocation ? "Open Settings" : "Allow always") {
                    if model.prefs.askedForAlwaysLocation { openSettings() } else {
                        model.prefs.askedForAlwaysLocation = true
                        model.regions.requestAlways()
                        model.permissionsChanged()
                    }
                }
            }
        }
        .task(id: model.permissionsRevision) { await refresh() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { Task { await refresh() } } }
    }

    private func step(_ text: String, _ action: String, action perform: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(text).font(.subheadline).foregroundStyle(.secondary)
            Button(action, action: perform).buttonStyle(.borderedProminent)
        }
    }

    private func refresh() async { notifications = await model.notifications.status() }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }
}

struct AccountView: View {
    @Environment(AppModel.self) private var model
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        Form {
            if let session = model.session {
                Section {
                    LabeledContent("Signed in as", value: session.email)
                    LabeledContent("Last synced") {
                        if let last = model.lastSyncAt { Text(last, style: .relative) + Text(" ago") } else { Text("Not yet") }
                    }
                }
                Section {
                    Button(model.isSyncing ? "Syncing…" : "Sync now") {
                        Task {
                            await model.syncNow()
                            show(model.syncError ?? "Everything is up to date.", error: model.syncError != nil)
                        }
                    }
                    .disabled(model.isSyncing)
                    Button("Sign out", role: .destructive) {
                        Task {
                            await model.signOut()
                            show("Signed out. Your data is still on this iPhone.")
                        }
                    }
                }
            } else {
                Section {
                    Text("Sign in to back up your coupons, places and memberships and keep them in step across your devices, including Android. Signing in to a different account than last time replaces the data on this iPhone.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    TextField("E-mail", text: $email)
                        .keyboardType(.emailAddress).textContentType(.username)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    SecureField("Password", text: $password).textContentType(.password)
                }
                Section {
                    Button(busy ? "Please wait…" : "Sign in") { run { await model.accounts.signIn(email: email, password: password) } }
                        .disabled(!canSubmit)
                    Button("Create account") { run { await model.accounts.signUp(email: email, password: password) } }
                        .disabled(!canSubmit)
                    Button("Forgot password?") {
                        guard email.contains("@") else { return show("Enter your e-mail address first.", error: true) }
                        run { await model.accounts.sendPasswordReset(email: email) }
                    }
                    .disabled(busy)
                }
            }
            if let message {
                Section { Text(message).foregroundStyle(isError ? .red : .secondary) }
            }
        }
        .navigationTitle("Backup & sync")
    }

    private var canSubmit: Bool { !busy && email.contains("@") && password.count >= 6 }

    private func run(_ call: @escaping () async -> AuthResult) {
        busy = true
        message = nil
        Task {
            let result = await call()
            busy = false
            switch result {
            case .signedIn: password = ""; show("Signed in. Syncing your data…")
            case .confirmationRequired: show("Check your inbox, then sign in.")
            case .failed(let text): show(text, error: true)
            }
        }
    }

    private func show(_ text: String, error: Bool = false) {
        message = text
        isError = error
    }
}

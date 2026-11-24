import PromoCodeCore
import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var router = model.router
        TabView(selection: $router.tab) {
            CouponListView()
                .tabItem { Label("Coupons", systemImage: "tag") }
                .tag(AppTab.coupons)
            MembershipListView()
                .tabItem { Label("Memberships", systemImage: "person.text.rectangle") }
                .tag(AppTab.memberships)
            PlaceListView()
                .tabItem { Label("Places", systemImage: "mappin.and.ellipse") }
                .tag(AppTab.places)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(AppTab.settings)
        }
    }
}

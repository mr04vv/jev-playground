import SwiftUI

@main
struct GestureLauncherApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        MenuBarExtra("RegisteredGesture Launcher", systemImage: "hand.draw") {
            MenuContent(model: model)
        }
        Window("RegisteredGesture Launcher 設定", id: SettingsView.windowID) {
            SettingsView(model: model)
        }
        .windowResizability(.contentSize)
    }
}

struct MenuContent: View {
    @ObservedObject var model: AppModel
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Text(model.status)
        Divider()
        ForEach(model.gestures) { gesture in
            Text("\(gesture.name)（お手本 \(gesture.samples.count) 回）")
        }
        if model.gestures.isEmpty {
            Text("ジェスチャー未登録")
        }
        Divider()
        Button("設定…") {
            openWindow(id: SettingsView.windowID)
            NSApp.activate(ignoringOtherApps: true)
        }
        Button("終了") { NSApp.terminate(nil) }
    }
}

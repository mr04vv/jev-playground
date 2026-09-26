import AppKit
import GestureCore
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    static let windowID = "settings"
    static let width: CGFloat = 460
    static let minHeight: CGFloat = 600

    @ObservedObject var model: AppModel
    @State private var name = ""
    @State private var actionKind = ActionKind.app
    @State private var appBundleID = ""
    @State private var appName = ""
    @State private var shortcutName = ""

    enum ActionKind: String, CaseIterable, Identifiable {
        case app = "アプリを起動"
        case shortcut = "ショートカットを実行"
        var id: Self { self }
    }

    var body: some View {
        Form {
            Section("TypeSafe API キー") {
                HStack {
                    SecureField("API キー", text: $model.apiKey)
                    Button("保存") { model.saveAPIKey() }
                }
            }

            Section("登録済みジェスチャー") {
                if model.gestures.isEmpty {
                    Text("まだありません").foregroundStyle(.secondary)
                }
                ForEach(model.gestures) { gesture in
                    HStack {
                        Text(gesture.name)
                        Spacer()
                        Text(describe(gesture.action)).foregroundStyle(.secondary)
                        Button("削除", role: .destructive) { model.delete(gesture) }
                    }
                }
            }

            Section("ジェスチャーを追加") {
                TextField("名前（例: 円）", text: $name)
                Picker("動作", selection: $actionKind) {
                    ForEach(ActionKind.allCases) { Text($0.rawValue).tag($0) }
                }
                if actionKind == .app {
                    HStack {
                        Text(appName.isEmpty ? "未選択" : appName)
                        Spacer()
                        Button("アプリを選ぶ…") { chooseApp() }
                    }
                } else {
                    TextField("ショートカット名", text: $shortcutName)
                }
                if let registration = model.registration {
                    HStack {
                        Text("描画待ち: \(registration.samples.count)/\(AppModel.samplesPerGesture) 回")
                        Spacer()
                        Button("キャンセル") { model.cancelRegistration() }
                    }
                } else {
                    Button("お手本を描いて登録（\(AppModel.samplesPerGesture) 回）") { startRegistration() }
                        .disabled(!canRegister)
                }
            }

            Text(model.status).foregroundStyle(.secondary)
        }
        .formStyle(.grouped)
        .frame(width: Self.width)
        .frame(minHeight: Self.minHeight)
    }

    private var canRegister: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && (actionKind == .app ? !appBundleID.isEmpty : !shortcutName.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    private func startRegistration() {
        let action: GestureAction = actionKind == .app
            ? .app(bundleID: appBundleID)
            : .shortcut(name: shortcutName.trimmingCharacters(in: .whitespaces))
        model.startRegistration(name: name.trimmingCharacters(in: .whitespaces), action: action)
        name = ""
    }

    private func chooseApp() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.application]
        panel.directoryURL = URL(fileURLWithPath: "/Applications")
        guard panel.runModal() == .OK, let url = panel.url, let bundle = Bundle(url: url),
              let bundleID = bundle.bundleIdentifier else { return }
        appBundleID = bundleID
        appName = FileManager.default.displayName(atPath: url.path)
    }

    private func describe(_ action: GestureAction) -> String {
        switch action {
        case .app(let bundleID):
            NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID)
                .map { FileManager.default.displayName(atPath: $0.path) } ?? bundleID
        case .shortcut(let name):
            "ショートカット: \(name)"
        }
    }
}

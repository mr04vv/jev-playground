import AppKit
import GestureCore

enum ActionRunner {
    struct AppNotFound: LocalizedError {
        let bundleID: String
        var errorDescription: String? { "アプリが見つかりません: \(bundleID)" }
    }

    static func run(_ action: GestureAction) throws {
        switch action {
        case .app(let bundleID):
            guard let url = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleID) else {
                throw AppNotFound(bundleID: bundleID)
            }
            NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration()) { _, error in
                if let error { NSLog("[GestureLauncher] failed to open %@: %@", bundleID, String(describing: error)) }
            }
        case .shortcut(let name):
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/shortcuts")
            process.arguments = ["run", name]
            try process.run()
        }
    }
}

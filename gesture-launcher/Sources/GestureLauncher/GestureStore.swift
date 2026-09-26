import Foundation
import GestureCore

/// Persists gestures as JSON under ~/Library/Application Support/GestureLauncher.
struct GestureStore {
    let url: URL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("GestureLauncher", isDirectory: true)
        .appendingPathComponent("gestures.json")

    func load() throws -> [RegisteredGesture] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        return try JSONDecoder().decode([RegisteredGesture].self, from: Data(contentsOf: url))
    }

    func save(_ gestures: [RegisteredGesture]) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(gestures).write(to: url, options: .atomic)
    }
}

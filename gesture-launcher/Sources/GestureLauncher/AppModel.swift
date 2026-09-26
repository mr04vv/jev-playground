import AppKit
import GestureCore
import SwiftUI

@MainActor
final class AppModel: ObservableObject {
    static let samplesPerGesture = 3
    static let confidenceThreshold = 0.6

    @Published private(set) var gestures: [RegisteredGesture] = []
    @Published private(set) var status = "⌥ を押しながらマウスを動かすとジェスチャーを判定します"
    @Published private(set) var registration: Registration?
    @Published var apiKey: String = Keychain.load() ?? ""

    struct Registration {
        var name: String
        var action: GestureAction
        var samples: [StrokeFeatures] = []
    }

    private let store = GestureStore()
    private let tracker = StrokeTracker()
    private let hud = HUD()

    init() {
        do {
            gestures = try store.load()
        } catch {
            report("ジェスチャーの読み込みに失敗しました: \(error.localizedDescription)")
        }
        tracker.onStroke = { [weak self] points, durationMs in
            self?.handle(points: points, durationMs: durationMs)
        }
        tracker.start()
    }

    func saveAPIKey() {
        do {
            try Keychain.save(apiKey.trimmingCharacters(in: .whitespacesAndNewlines))
            report("API キーを保存しました")
        } catch {
            report("API キーの保存に失敗しました: \(error.localizedDescription)")
        }
    }

    func startRegistration(name: String, action: GestureAction) {
        registration = Registration(name: name, action: action)
        report("⌥ を押しながら「\(name)」を描いてください（1/\(Self.samplesPerGesture)）")
    }

    func cancelRegistration() {
        registration = nil
        report("登録をキャンセルしました")
    }

    func delete(_ gesture: RegisteredGesture) {
        gestures.removeAll { $0.id == gesture.id }
        persist()
    }

    private func handle(points: [GestureCore.Point], durationMs: Int) {
        guard let features = StrokeFeatures(points: points, durationMs: durationMs) else {
            report("短すぎる・長すぎるストロークは無視しました", hud: registration != nil)
            return
        }
        if registration != nil {
            addSample(features)
        } else {
            recognize(features)
        }
    }

    private func addSample(_ features: StrokeFeatures) {
        guard var current = registration else { return }
        current.samples.append(features)
        if current.samples.count < Self.samplesPerGesture {
            registration = current
            report("もう一度「\(current.name)」を描いてください（\(current.samples.count + 1)/\(Self.samplesPerGesture)）", hud: true)
            return
        }
        gestures.append(RegisteredGesture(id: UUID().uuidString, name: current.name, action: current.action, samples: current.samples))
        registration = nil
        persist()
        report("「\(current.name)」を登録しました", hud: true)
    }

    private func recognize(_ features: StrokeFeatures) {
        guard !gestures.isEmpty else {
            report("ジェスチャーが未登録です。設定から登録してください", hud: true)
            return
        }
        guard !apiKey.isEmpty else {
            report("API キーが未設定です。設定から入力してください", hud: true)
            return
        }
        let client = JevClient(apiKey: apiKey)
        let candidates = gestures
        report("判定中…", hud: true)
        Task {
            do {
                let started = Date()
                let response = try await client.recognize(features, among: candidates)
                let elapsed = Int(Date().timeIntervalSince(started) * 1000)
                let confidence = response.answer.map { String(format: "%.2f", $0.confidence) } ?? "-"
                guard let id = response.decision(threshold: Self.confidenceThreshold),
                      let gesture = candidates.first(where: { $0.id == id }) else {
                    report("該当なし（確信度 \(confidence)、\(elapsed)ms）", hud: true)
                    return
                }
                try ActionRunner.run(gesture.action)
                report("「\(gesture.name)」を実行（確信度 \(confidence)、\(elapsed)ms）", hud: true)
            } catch {
                NSLog("[GestureLauncher] recognition failed: %@", String(describing: error))
                report("判定に失敗しました: \(error.localizedDescription)", hud: true)
            }
        }
    }

    private func persist() {
        do {
            try store.save(gestures)
        } catch {
            report("ジェスチャーの保存に失敗しました: \(error.localizedDescription)")
        }
    }

    private func report(_ message: String, hud showHUD: Bool = false) {
        status = message
        if showHUD { hud.show(message) }
    }
}

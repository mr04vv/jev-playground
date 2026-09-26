import AppKit
import GestureCore

/// Records the mouse path while ⌥ (and only ⌥) is held.
/// Watches mouse-moved events and reads modifier flags from NSEvent's class properties,
/// neither of which requires the Accessibility permission.
@MainActor
final class StrokeTracker {
    static let releasePollInterval: TimeInterval = 0.03

    var onStroke: (([GestureCore.Point], Int) -> Void)?

    private var points: [GestureCore.Point] = []
    private var startedAt: Date?
    private var monitors: [Any] = []
    private var releaseTimer: Timer?

    private var isTriggerHeld: Bool {
        NSEvent.modifierFlags.intersection(.deviceIndependentFlagsMask) == .option
    }

    func start() {
        // The global monitor misses events while this app is active, so a local one covers that case.
        if let global = NSEvent.addGlobalMonitorForEvents(matching: .mouseMoved, handler: { [weak self] _ in
            MainActor.assumeIsolated { self?.mouseMoved() }
        }) {
            monitors.append(global)
        }
        if let local = NSEvent.addLocalMonitorForEvents(matching: .mouseMoved, handler: { [weak self] event in
            MainActor.assumeIsolated { self?.mouseMoved() }
            return event
        }) {
            monitors.append(local)
        }
    }

    private func mouseMoved() {
        guard isTriggerHeld else { return }
        let location = NSEvent.mouseLocation
        if startedAt == nil {
            startedAt = Date()
            points = []
            releaseTimer = Timer.scheduledTimer(withTimeInterval: Self.releasePollInterval, repeats: true) { [weak self] _ in
                MainActor.assumeIsolated { self?.checkRelease() }
            }
        }
        points.append(GestureCore.Point(x: location.x, y: location.y))
    }

    private func checkRelease() {
        guard !isTriggerHeld, let startedAt else { return }
        releaseTimer?.invalidate()
        releaseTimer = nil
        self.startedAt = nil
        let durationMs = Int(Date().timeIntervalSince(startedAt) * 1000)
        onStroke?(points, durationMs)
    }
}

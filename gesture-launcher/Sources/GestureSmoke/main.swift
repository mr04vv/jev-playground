// Measures recognition accuracy on synthetic strokes against the real API.
// Usage: TYPESAFE_API_KEY=... swift run GestureSmoke
import Foundation
import GestureCore

let trialsPerGesture = 4
let samplesPerGesture = 3
let jitter = 12.0
let durationMs = 600

func polyline(_ corners: [(Double, Double)], stepsPerSegment: Int = 12) -> [Point] {
    var points: [Point] = []
    for (a, b) in zip(corners, corners.dropFirst()) {
        for i in 0..<stepsPerSegment {
            let t = Double(i) / Double(stepsPerSegment)
            points.append(Point(x: a.0 + (b.0 - a.0) * t, y: a.1 + (b.1 - a.1) * t))
        }
    }
    if let last = corners.last { points.append(Point(x: last.0, y: last.1)) }
    return points
}

let circle = (0...40).map { i -> Point in
    let a = Double(i) / 40 * 2 * .pi
    return Point(x: 150 + 100 * cos(a), y: 150 + 100 * sin(a))
}

let shapes: [(name: String, points: [Point])] = [
    ("円", circle),
    ("Z", polyline([(0, 200), (200, 200), (0, 0), (200, 0)])),
    ("L", polyline([(0, 300), (0, 0), (150, 0)])),
    ("上へはらう", polyline([(0, 0), (60, 250)])),
    ("V", polyline([(0, 200), (100, 0), (200, 200)])),
]

func distorted(_ points: [Point]) -> [Point] {
    let scale = Double.random(in: 0.6...1.6)
    let dx = Double.random(in: 0...800), dy = Double.random(in: 0...600)
    return points.map {
        Point(x: $0.x * scale + dx + Double.random(in: -jitter...jitter),
              y: $0.y * scale + dy + Double.random(in: -jitter...jitter))
    }
}

func features(_ points: [Point]) -> StrokeFeatures {
    guard let f = StrokeFeatures(points: distorted(points), durationMs: durationMs) else {
        fatalError("synthetic stroke rejected")
    }
    return f
}

guard let apiKey = ProcessInfo.processInfo.environment["TYPESAFE_API_KEY"], !apiKey.isEmpty else {
    fatalError("TYPESAFE_API_KEY is not set")
}

let gestures = shapes.map { shape in
    RegisteredGesture(id: shape.name, name: shape.name, action: .shortcut(name: "noop"),
                      samples: (0..<samplesPerGesture).map { _ in features(shape.points) })
}
let client = JevClient(apiKey: apiKey)
var correct = 0
var total = 0
for shape in shapes {
    for _ in 0..<trialsPerGesture {
        let started = Date()
        let response = try await client.recognize(features(shape.points), among: gestures)
        let ms = Int(Date().timeIntervalSince(started) * 1000)
        let answer = response.answer
        let hit = answer?.choice == shape.name
        correct += hit ? 1 : 0
        total += 1
        print("\(hit ? "ok  " : "MISS") expected=\(shape.name) got=\(answer?.choice ?? "-") confidence=\(String(format: "%.2f", answer?.confidence ?? 0)) \(ms)ms")
    }
}
print("accuracy \(correct)/\(total)")

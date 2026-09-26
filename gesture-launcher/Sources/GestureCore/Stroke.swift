import Foundation

public struct Point: Codable, Equatable, Sendable {
    public var x: Double
    public var y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }
}

private func distance(_ a: Point, _ b: Point) -> Double {
    hypot(b.x - a.x, b.y - a.y)
}

func pathLength(_ points: [Point]) -> Double {
    zip(points, points.dropFirst()).reduce(0) { $0 + distance($1.0, $1.1) }
}

/// Resample a stroke into `count` points evenly spaced along its path ($1 recognizer style).
public func resample(_ points: [Point], count: Int) -> [Point] {
    guard let first = points.first, let last = points.last, points.count > 1, count > 1 else { return points }
    let step = pathLength(points) / Double(count - 1)
    guard step > 0 else { return Array(repeating: first, count: count) }

    var source = points
    var result = [first]
    var carried = 0.0
    var i = 1
    while i < source.count, result.count < count {
        let d = distance(source[i - 1], source[i])
        if d > 0, carried + d >= step {
            let t = (step - carried) / d
            let q = Point(
                x: source[i - 1].x + t * (source[i].x - source[i - 1].x),
                y: source[i - 1].y + t * (source[i].y - source[i - 1].y)
            )
            result.append(q)
            source.insert(q, at: i)
            carried = 0
        } else {
            carried += d
        }
        i += 1
    }
    while result.count < count { result.append(last) }
    return result
}

/// Translate to the origin and scale so the longer side of the bounding box becomes 1, keeping aspect.
public func normalize(_ points: [Point]) -> [Point] {
    guard let minX = points.map(\.x).min(), let maxX = points.map(\.x).max(),
          let minY = points.map(\.y).min(), let maxY = points.map(\.y).max() else { return points }
    let scale = max(maxX - minX, maxY - minY)
    guard scale > 0 else { return points.map { _ in Point(x: 0, y: 0) } }
    return points.map { Point(x: ($0.x - minX) / scale, y: ($0.y - minY) / scale) }
}

/// Numpad-style direction codes (6 = right, 8 = up, 2 = down, 4 = left, 9/7/3/1 diagonals), y growing upward.
private let sectorCodes: [Int: Character] = [0: "6", 1: "9", 2: "8", 3: "7", 4: "4", -4: "4", -3: "1", -2: "2", -1: "3"]

/// Runs shorter than this are treated as noise, e.g. the single diagonal segment cutting a corner.
private let minDirectionRun = 2

private func directionRuns(_ points: [Point]) -> [Character] {
    let codes = zip(points, points.dropFirst()).compactMap { a, b -> Character? in
        guard distance(a, b) > 0 else { return nil }
        let sector = Int((atan2(b.y - a.y, b.x - a.x) / (.pi / 4)).rounded())
        return sectorCodes[sector]
    }
    var runs: [(code: Character, length: Int)] = []
    for code in codes {
        if runs.last?.code == code { runs[runs.count - 1].length += 1 } else { runs.append((code, 1)) }
    }
    var kept: [Character] = []
    for run in runs where run.length >= minDirectionRun && kept.last != run.code {
        kept.append(run.code)
    }
    return kept
}

/// Comma-separated direction codes with repeats and noise removed, e.g. "2,6" for an L.
public func directions(_ points: [Point]) -> String {
    directionRuns(points).map(String.init).joined(separator: ",")
}

private func rounded(_ value: Double) -> Double {
    (value * 100).rounded() / 100
}

/// Shape features sent to Jev: independent of where and how large the stroke was drawn.
public struct StrokeFeatures: Codable, Equatable, Sendable {
    public static let pointCount = 32
    public static let minDurationMs = 150
    public static let maxDurationMs = 6000
    public static let minPathLength = 40.0

    /// Resampled points in the unit box, as [x, y] pairs rounded to two decimals.
    public var points: [[Double]]
    public var directions: String
    /// Number of direction changes.
    public var corners: Int
    /// Width divided by height of the bounding box.
    public var aspect: Double

    public init(points: [[Double]], directions: String, corners: Int, aspect: Double) {
        self.points = points
        self.directions = directions
        self.corners = corners
        self.aspect = aspect
    }

    public init?(points raw: [Point], durationMs: Int) {
        guard (Self.minDurationMs...Self.maxDurationMs).contains(durationMs),
              pathLength(raw) >= Self.minPathLength else { return nil }
        let unit = normalize(resample(raw, count: Self.pointCount))
        let runs = directionRuns(unit)
        let width = unit.map(\.x).max()! - unit.map(\.x).min()!
        let height = unit.map(\.y).max()! - unit.map(\.y).min()!
        self.init(
            points: unit.map { [rounded($0.x), rounded($0.y)] },
            directions: runs.map(String.init).joined(separator: ","),
            corners: max(runs.count - 1, 0),
            aspect: height > 0 ? rounded(width / height) : 0
        )
    }
}

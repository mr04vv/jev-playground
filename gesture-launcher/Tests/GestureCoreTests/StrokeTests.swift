import Testing
@testable import GestureCore

private func line(from a: Point, to b: Point, steps: Int) -> [Point] {
    (0...steps).map { i in
        let t = Double(i) / Double(steps)
        return Point(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
    }
}

// An "L": down, then right (y grows upward, as in Cocoa screen coordinates).
private let lShape = line(from: Point(x: 0, y: 300), to: Point(x: 0, y: 0), steps: 30)
    + line(from: Point(x: 0, y: 0), to: Point(x: 150, y: 0), steps: 15).dropFirst()

@Test func resampleProducesEvenlySpacedPoints() {
    let points = resample([Point(x: 0, y: 0), Point(x: 100, y: 0)], count: 5)
    #expect(points.map(\.x) == [0, 25, 50, 75, 100])
}

@Test func normalizeFitsTheLongerSideIntoTheUnitBox() {
    let points = normalize([Point(x: 10, y: 10), Point(x: 210, y: 110)])
    #expect(points == [Point(x: 0, y: 0), Point(x: 1, y: 0.5)])
}

@Test func directionsCollapseRepeatedMoves() {
    let points = resample(lShape, count: StrokeFeatures.pointCount)
    #expect(directions(points) == "2,6")
}

@Test func featuresDescribeTheShapeIndependentlyOfPositionAndSize() throws {
    let small = try #require(StrokeFeatures(points: lShape, durationMs: 400))
    let moved = lShape.map { Point(x: $0.x * 2 + 500, y: $0.y * 2 + 300) }
    let large = try #require(StrokeFeatures(points: moved, durationMs: 400))
    #expect(small.points.count == StrokeFeatures.pointCount)
    #expect(small.directions == "2,6")
    #expect(small.corners == 1)
    #expect(small == large)
}

@Test func featuresRejectStrokesThatAreTooShortOrTooLong() {
    #expect(StrokeFeatures(points: [Point(x: 0, y: 0), Point(x: 5, y: 0)], durationMs: 400) == nil)
    #expect(StrokeFeatures(points: lShape, durationMs: 50) == nil)
    #expect(StrokeFeatures(points: lShape, durationMs: 10_000) == nil)
}

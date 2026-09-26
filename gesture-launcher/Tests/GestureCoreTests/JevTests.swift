import Foundation
import Testing
@testable import GestureCore

private let features = StrokeFeatures(points: [[0, 0], [1, 0]], directions: "6", corners: 0, aspect: 1)

@Test func requestListsGestureIdsPlusUnknownAsChoices() throws {
    let gestures = [Gesture(id: "g1", name: "円", action: .shortcut(name: "x"), samples: [features])]
    let data = try JSONEncoder().encode(JevRequest(input: features, gestures: gestures))
    let json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    #expect(json["model"] as? String == "jev-latest")
    let state = try #require(json["state"] as? [String: Any])
    let examples = try #require(state["examples"] as? [[String: Any]])
    #expect(examples.first?["id"] as? String == "g1")
    let question = try #require((json["questions"] as? [String: Any])?[JevRequest.questionKey] as? [String: Any])
    #expect(question["type"] as? String == "choice")
    let criteria = try #require(question["criteria"] as? [String: Any])
    #expect(Set(criteria.keys) == ["g1", JevRequest.unknown])
}

@Test func decisionRequiresAKnownChoiceAboveTheThreshold() throws {
    let body = #"{"answers":{"gesture":{"type":"choice","choice":"g1","confidence":0.8}}}"#
    let response = try JSONDecoder().decode(JevResponse.self, from: Data(body.utf8))
    #expect(response.decision(threshold: 0.6) == "g1")
    #expect(response.decision(threshold: 0.9) == nil)

    let unknown = #"{"answers":{"gesture":{"type":"choice","choice":"unknown","confidence":0.99}}}"#
    #expect(try JSONDecoder().decode(JevResponse.self, from: Data(unknown.utf8)).decision(threshold: 0.6) == nil)
}

@Test func gestureRoundTripsThroughJson() throws {
    let gesture = Gesture(id: "g1", name: "Slack", action: .app(bundleID: "com.tinyspeck.slackmacgap"), samples: [features])
    let decoded = try JSONDecoder().decode(Gesture.self, from: JSONEncoder().encode(gesture))
    #expect(decoded == gesture)
}

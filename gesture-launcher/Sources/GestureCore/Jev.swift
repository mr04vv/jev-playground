import Foundation

/// Body for POST https://api.typesafe.ai/v1/systemone, modeled on the Whistle approach:
/// the input stroke plus registered samples in state, and one choice over gesture ids and "unknown".
public struct JevRequest: Encodable, Sendable {
    public static let questionKey = "gesture"
    public static let unknown = "unknown"
    static let instructions = """
        Which registered gesture is the input mouse stroke? Compare the input with each gesture's samples \
        by shape, stroke order, and direction changes. Ignore where it was drawn, its size, and its speed.
        """
    static let unknownDescription = "The input does not clearly match any registered gesture."

    struct State: Encodable, Sendable {
        let input: StrokeFeatures
        let examples: [Example]
    }

    struct Example: Encodable, Sendable {
        let id: String
        let name: String
        let samples: [StrokeFeatures]
    }

    struct Question: Encodable, Sendable {
        let type: String
        let instructions: String
        let criteria: [String: String]
    }

    let model: String
    let state: State
    let questions: [String: Question]

    public init(input: StrokeFeatures, gestures: [Gesture], model: String = "jev-latest") {
        var criteria = Dictionary(uniqueKeysWithValues: gestures.map { ($0.id, $0.name) })
        criteria[Self.unknown] = Self.unknownDescription
        self.model = model
        self.state = State(input: input, examples: gestures.map { Example(id: $0.id, name: $0.name, samples: $0.samples) })
        self.questions = [Self.questionKey: Question(type: "choice", instructions: Self.instructions, criteria: criteria)]
    }
}

public struct JevResponse: Decodable, Sendable {
    public struct Answer: Decodable, Sendable {
        public let choice: String
        public let confidence: Double
    }

    public let answers: [String: Answer]

    public var answer: Answer? { answers[JevRequest.questionKey] }

    /// The gesture id to act on, or nil when Jev picked "unknown" or is not confident enough.
    public func decision(threshold: Double) -> String? {
        guard let answer, answer.choice != JevRequest.unknown, answer.confidence >= threshold else { return nil }
        return answer.choice
    }
}

public enum GestureAction: Codable, Equatable, Sendable {
    case app(bundleID: String)
    case shortcut(name: String)
}

public struct Gesture: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var action: GestureAction
    public var samples: [StrokeFeatures]

    public init(id: String, name: String, action: GestureAction, samples: [StrokeFeatures]) {
        self.id = id
        self.name = name
        self.action = action
        self.samples = samples
    }
}

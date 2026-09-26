import Foundation

public struct JevError: LocalizedError {
    public let status: Int
    public let requestID: String
    public let body: String

    public var errorDescription: String? { "Jev API \(status) (request \(requestID)): \(body)" }
}

public struct JevClient: Sendable {
    public static let endpoint = URL(string: "https://api.typesafe.ai/v1/systemone")!
    static let timeout: TimeInterval = 10

    let apiKey: String

    public init(apiKey: String) {
        self.apiKey = apiKey
    }

    public func recognize(_ input: StrokeFeatures, among gestures: [RegisteredGesture]) async throws -> JevResponse {
        var request = URLRequest(url: Self.endpoint, timeoutInterval: Self.timeout)
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONEncoder().encode(JevRequest(input: input, gestures: gestures))

        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as! HTTPURLResponse
        guard (200..<300).contains(http.statusCode) else {
            throw JevError(
                status: http.statusCode,
                requestID: http.value(forHTTPHeaderField: "x-typesafe-request-id") ?? "none",
                body: String(decoding: data, as: UTF8.self)
            )
        }
        return try JSONDecoder().decode(JevResponse.self, from: data)
    }
}

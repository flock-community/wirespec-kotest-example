package community.flock.library

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.matching.MatchResult
import com.github.tomakehurst.wiremock.matching.ValueMatcher
import community.flock.wirespec.integration.kotest.extension.MockServer
import community.flock.wirespec.integration.kotest.extension.MockStub
import community.flock.wirespec.integration.wiremock.kotlin.requestBuilder
import community.flock.wirespec.integration.wiremock.kotlin.responseBuilder
import community.flock.wirespec.integration.wiremock.kotlin.toRawRequest

/** The [MockServer] behind `.mock { }`: every stub becomes a WireMock mapping. */
class WireMockMockServer private constructor(
    private val server: WireMockServer,
) : MockServer,
    AutoCloseable {

    val baseUrl: String get() = server.baseUrl()

    override fun stub(stub: MockStub) {
        server.stubFor(
            requestBuilder(stub.method, stub.pathTemplate)
                .andMatching(ValueMatcher<Request> { if (stub.accepts(it)) MatchResult.exactMatch() else MatchResult.noMatch() })
                .willReturn(responseBuilder(stub.response)),
        )
    }

    override fun reset(): Unit = server.resetAll()

    override fun close(): Unit = server.stop()

    companion object {
        fun start(): WireMockMockServer = WireMockMockServer(WireMockServer(options().dynamicPort()).apply { start() })
    }
}

// The typed predicate deserializes the request; one that does not fit this stub is simply not a match
private fun MockStub.accepts(request: Request): Boolean = runCatching { matches(request.toRawRequest()) }.getOrDefault(false)

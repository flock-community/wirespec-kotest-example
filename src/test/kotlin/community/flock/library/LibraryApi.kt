package community.flock.library

import community.flock.wirespec.integration.jvm.transport.HttpTransportation
import community.flock.wirespec.integration.kotest.extension.WirespecEndpointExtension
import community.flock.wirespec.kotlin.Wirespec
import io.kotest.extensions.spring.testContextManager
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext

/**
 * Backs `.call()` in specs that register it: requests go over real HTTP to the application
 * started by @SpringBootTest, (de)serialized by the application's own Wirespec.Serialization.
 */
val libraryApi = WirespecEndpointExtension(
    serialization = { serialization() },
    transportation = { HttpTransportation("http://localhost:${property("local.server.port")}") },
)

suspend fun serialization(): Wirespec.Serialization = applicationContext().getBean<Wirespec.Serialization>()

suspend fun applicationContext(): ApplicationContext = testContextManager().testContext.applicationContext

suspend fun property(name: String): String = applicationContext().environment.getRequiredProperty(name)

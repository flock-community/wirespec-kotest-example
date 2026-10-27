package community.flock.library

import community.flock.wirespec.integration.spring.kotlin.configuration.EnableWirespecController
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableWirespecController
class LibraryApplication

fun main(args: Array<String>) {
    runApplication<LibraryApplication>(*args)
}

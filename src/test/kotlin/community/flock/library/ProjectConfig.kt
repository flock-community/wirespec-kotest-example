package community.flock.library

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(
        // Starts the Spring context of every @SpringBootTest spec and injects beans into its constructor
        SpringExtension(),
    )
}

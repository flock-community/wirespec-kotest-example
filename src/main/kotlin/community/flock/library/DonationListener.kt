package community.flock.library

import community.flock.library.contract.model.BookInput
import community.flock.wirespec.kotlin.Wirespec
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import kotlin.reflect.typeOf

const val BOOK_DONATIONS_TOPIC = "book-donations"

/** Consumes the BookDonations channel: every donated book with a valid ISBN goes on the shelf. */
@Component
class DonationListener(
    private val library: Library,
    private val serialization: Wirespec.Serialization,
) {
    @KafkaListener(topics = [BOOK_DONATIONS_TOPIC])
    fun onDonation(message: ByteArray) {
        serialization.deserializeBody<BookInput>(message, typeOf<BookInput>())
            .takeIf { it.isbn.validate() }
            ?.let(library::add)
    }
}

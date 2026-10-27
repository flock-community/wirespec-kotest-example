package community.flock.library

import community.flock.library.contract.channel.BookDonations
import community.flock.library.contract.endpoint.ListBooks
import community.flock.library.contract.kotest.call
import community.flock.library.contract.kotest.generate
import community.flock.library.contract.kotest.send
import community.flock.library.contract.model.BookInput
import community.flock.library.contract.model.Genre
import community.flock.wirespec.integration.kotest.extension.WirespecChannelExtension
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.kafka.test.context.EmbeddedKafka
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.kafka.listener.auto-startup=true"])
@EmbeddedKafka(partitions = 1, topics = [BOOK_DONATIONS_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class DonationTest :
    FunSpec({
        extension(libraryApi)
        // Backs `.send()`: publishes generated messages to the embedded Kafka broker
        extension(
            WirespecChannelExtension(
                serialization = { serialization() },
                transportation = { KafkaChannelTransport(property("spring.kafka.bootstrap-servers")) },
            ),
        )

        test("a donated book ends up on the shelf") {
            val donation = BookDonations.generate.message { title("The Hobbit") }.send(BOOK_DONATIONS_TOPIC)

            eventually(15.seconds) {
                shelf() shouldContain donation
            }
        }

        test("donations of every genre end up on the shelf") {
            val donations = Genre.entries.map { genre ->
                BookDonations.generate.message { genre(genre) }.send(BOOK_DONATIONS_TOPIC)
            }

            eventually(15.seconds) {
                shelf() shouldContainAll donations
            }
        }

        test("a donation with an invalid ISBN is not put on the shelf") {
            val invalid = BookDonations.generate.message { isbn("not-an-isbn") }.send(BOOK_DONATIONS_TOPIC)
            val valid = BookDonations.generate.message { }.send(BOOK_DONATIONS_TOPIC)

            // Donations on one partition are consumed in order: once the valid one is in, the invalid one was skipped
            eventually(15.seconds) {
                shelf() shouldContain valid
            }
            shelf() shouldNotContain invalid
        }
    })

private suspend fun shelf(): List<BookInput> = ListBooks.generate.request { }.call()
    .shouldBeInstanceOf<ListBooks.Response200>()
    .body
    .map { BookInput(isbn = it.isbn, title = it.title, author = it.author, genre = it.genre) }

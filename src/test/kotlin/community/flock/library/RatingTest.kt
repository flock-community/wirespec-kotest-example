package community.flock.library

import community.flock.library.contract.endpoint.GetRating
import community.flock.library.contract.endpoint.GetReviews
import community.flock.library.contract.kotest.call
import community.flock.library.contract.kotest.generate
import community.flock.library.contract.kotest.mock
import community.flock.library.contract.model.Book
import community.flock.library.contract.model.Rating
import community.flock.library.contract.model.Review
import community.flock.wirespec.integration.kotest.dsl.draw
import community.flock.wirespec.integration.kotest.extension.WirespecMockExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = RANDOM_PORT)
class RatingTest :
    FunSpec({
        extension(libraryApi)
        // Backs `.mock { }`: stubs go to the WireMock server, and are reset before every test
        extension(WirespecMockExtension(server = reviewService, serialization = { serialization() }))
        afterSpec { reviewService.close() }

        test("the rating is the average of the book's reviews") {
            val book = addBook()
            val reviews = listOf(5L, 4L, 3L).map { stars ->
                Review.generate {
                    isbn(book.isbn.value)
                    stars(stars)
                }.draw()
            }

            GetReviews.generate.response200 { body(reviews) }
                .mock { request -> request.queries.isbn == book.isbn.value }

            ratingOf(book) shouldBe Rating(bookId = book.id, reviews = 3, average = 4.0)
        }

        test("a book without reviews has no average") {
            val book = addBook()

            GetReviews.generate.response200 { body(emptyList()) }
                .mock { request -> request.queries.isbn == book.isbn.value }

            ratingOf(book) shouldBe Rating(bookId = book.id, reviews = 0, average = null)
        }

        test("whatever the review platform answers, the average stays within the stars its contract allows") {
            checkAll(20, Arb.list(Review.generate(), 1..25)) { reviews ->
                val book = addBook()

                GetReviews.generate.response200 { body(reviews) }
                    .mock { request -> request.queries.isbn == book.isbn.value }

                val rating = ratingOf(book)
                rating.reviews shouldBe reviews.size.toLong()
                rating.average.shouldNotBeNull() shouldBeIn 1.0..5.0
                rating.average shouldBe (reviews.map { it.stars }.average() plusOrMinus 1e-9)
            }
        }
    }) {
    companion object {
        // Started before the Spring context, so the application can be pointed at it
        private val reviewService = WireMockMockServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun reviewsBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("reviews.base-url") { reviewService.baseUrl }
        }
    }
}

private suspend fun ratingOf(book: Book): Rating = GetRating.generate.request { path { id(book.id) } }.call()
    .shouldBeInstanceOf<GetRating.Response200>()
    .body

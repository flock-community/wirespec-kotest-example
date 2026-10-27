package community.flock.library

import community.flock.library.contract.endpoint.AddBook
import community.flock.library.contract.generator.BookInputGenerator
import community.flock.library.contract.kotest.generate
import community.flock.library.contract.model.BookInput
import community.flock.library.contract.model.Genre
import community.flock.wirespec.integration.kotest.dsl.draw
import community.flock.wirespec.integration.kotest.generator.kotestWirespecKotlinGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.checkAll

// No Spring and no HTTP: the generated DSL is a set of Kotest generators for the contract's types
class GeneratedDataTest :
    FunSpec({

        test("every generated book satisfies the contract") {
            checkAll(BookInput.generate()) { book ->
                book.isbn.validate() shouldBe true
            }
        }

        test("pin the fields a test is about, generate the rest") {
            val book = BookInput.generate {
                title("Dune")
                genre(Genre.FICTION)
            }.draw()

            book.title shouldBe "Dune"
            book.genre shouldBe Genre.FICTION
            book.isbn.validate() shouldBe true
        }

        test("requests are generated the same way") {
            val request = AddBook.generate.request {
                body { author("Frank Herbert") }
            }.draw()

            request.body.author shouldBe "Frank Herbert"
        }

        test("the same seed generates the same data") {
            val first = BookInputGenerator.generate(kotestWirespecKotlinGenerator(seed = 42), emptyList())
            val second = BookInputGenerator.generate(kotestWirespecKotlinGenerator(seed = 42), emptyList())
            val other = BookInputGenerator.generate(kotestWirespecKotlinGenerator(seed = 7), emptyList())

            first shouldBe second
            first shouldNotBe other
        }
    })

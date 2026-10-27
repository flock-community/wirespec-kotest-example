package community.flock.library

import community.flock.library.contract.endpoint.BorrowBook
import community.flock.library.contract.endpoint.GetBook
import community.flock.library.contract.endpoint.ListBooks
import community.flock.library.contract.endpoint.ReturnBook
import community.flock.library.contract.kotest.call
import community.flock.library.contract.kotest.generate
import community.flock.library.contract.model.Genre
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.greekCoptic
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.printableAscii
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT)
class BookPropertiesTest :
    FunSpec({
        extension(libraryApi)

        test("books of every genre can be listed by that genre") {
            checkAll(Exhaustive.enum<Genre>()) { genre ->
                val book = addBook { genre(genre) }

                ListBooks.generate.request { query { genre(genre) } }.call()
                    .shouldBeInstanceOf<ListBooks.Response200>()
                    .body.shouldContain(book)
                    .shouldForAll { it.genre shouldBe genre }
            }
        }

        test("titles and authors survive the round trip, whatever characters they contain") {
            val text = Arb.string(1..40, Arb.choice(Codepoint.printableAscii(), Codepoint.cyrillic(), Codepoint.greekCoptic(), Codepoint.egyptianHieroglyphs()))

            checkAll(50, text, text) { anyTitle, anyAuthor ->
                val book = addBook {
                    title(anyTitle)
                    author(anyAuthor)
                }

                book.title shouldBe anyTitle
                book.author shouldBe anyAuthor
                GetBook.generate.request { path { id(book.id) } }.call()
                    .shouldBeInstanceOf<GetBook.Response200>()
                    .body shouldBe book
            }
        }

        test("a book can only be borrowed when it is available and only be returned when it is on loan") {
            checkAll(25, Arb.list(Arb.enum<LoanAction>(), 1..10)) { actions ->
                val book = addBook()

                actions.fold(true) { available, action ->
                    val accepted = when (action) {
                        LoanAction.BORROW -> BorrowBook.generate.request { path { id(book.id) } }.call() is BorrowBook.Response200
                        LoanAction.RETURN -> ReturnBook.generate.request { path { id(book.id) } }.call() is ReturnBook.Response200
                    }
                    accepted shouldBe (available == (action == LoanAction.BORROW))
                    available != accepted
                }
            }
        }
    })

private enum class LoanAction { BORROW, RETURN }

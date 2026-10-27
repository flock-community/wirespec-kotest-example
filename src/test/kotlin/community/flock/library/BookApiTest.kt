package community.flock.library

import community.flock.library.contract.endpoint.AddBook
import community.flock.library.contract.endpoint.BorrowBook
import community.flock.library.contract.endpoint.GetBook
import community.flock.library.contract.endpoint.ListBooks
import community.flock.library.contract.endpoint.ReturnBook
import community.flock.library.contract.kotest.BookInputBuilder
import community.flock.library.contract.kotest.call
import community.flock.library.contract.kotest.generate
import community.flock.library.contract.model.Book
import community.flock.library.contract.model.BookId
import community.flock.library.contract.model.Genre
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import java.util.UUID

@SpringBootTest(webEnvironment = RANDOM_PORT)
class BookApiTest :
    FunSpec({
        extension(libraryApi)

        test("a book can be added and fetched back") {
            val added = AddBook.generate.request {
                body { title("Dune") }
            }.call()
            added.shouldBeInstanceOf<AddBook.Response201>()

            val fetched = GetBook.generate.request {
                path { id(added.body.id) }
            }.call()
            fetched.shouldBeInstanceOf<GetBook.Response200>()

            fetched.body shouldBe added.body
            fetched.body.title shouldBe "Dune"
            fetched.body.available shouldBe true
        }

        test("a book cannot be borrowed twice") {
            val book = addBook()

            BorrowBook.generate.request { path { id(book.id) } }.call()
                .shouldBeInstanceOf<BorrowBook.Response200>()
                .body.available shouldBe false
            BorrowBook.generate.request { path { id(book.id) } }.call()
                .shouldBeInstanceOf<BorrowBook.Response409>()
        }

        test("a book that is not on loan cannot be returned") {
            val book = addBook()

            ReturnBook.generate.request { path { id(book.id) } }.call()
                .shouldBeInstanceOf<ReturnBook.Response409>()
        }

        test("an unknown book is not found") {
            GetBook.generate.request { path { id(BookId(UUID.randomUUID().toString())) } }.call()
                .shouldBeInstanceOf<GetBook.Response404>()
        }

        test("an invalid ISBN is rejected") {
            AddBook.generate.request { body { isbn("not-an-isbn") } }.call()
                .shouldBeInstanceOf<AddBook.Response400>()
        }

        test("books can be listed by genre") {
            val science = addBook { genre(Genre.SCIENCE) }

            ListBooks.generate.request { query { genre(Genre.SCIENCE) } }.call()
                .shouldBeInstanceOf<ListBooks.Response200>()
                .body.shouldContain(science)
                .shouldForAll { it.genre shouldBe Genre.SCIENCE }
        }
    })

suspend fun addBook(block: BookInputBuilder.() -> Unit = {}): Book = AddBook.generate.request { body(block) }.call()
    .shouldBeInstanceOf<AddBook.Response201>()
    .body

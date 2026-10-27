package community.flock.library

import community.flock.library.contract.endpoint.AddBook
import community.flock.library.contract.endpoint.BorrowBook
import community.flock.library.contract.endpoint.GetBook
import community.flock.library.contract.endpoint.ListBooks
import community.flock.library.contract.endpoint.ReturnBook
import community.flock.library.contract.model.ApiError
import community.flock.library.contract.model.BookId
import org.springframework.web.bind.annotation.RestController

@RestController
class BookController(
    private val library: Library,
) : ListBooks.Handler,
    AddBook.Handler,
    GetBook.Handler,
    BorrowBook.Handler,
    ReturnBook.Handler {

    override suspend fun listBooks(request: ListBooks.Request): ListBooks.Response<*> =
        ListBooks.Response200(library.list(request.queries.genre))

    override suspend fun addBook(request: AddBook.Request): AddBook.Response<*> = when {
        request.body.isbn.validate() -> AddBook.Response201(library.add(request.body))
        else -> AddBook.Response400(ApiError(code = 400, message = "Invalid ISBN: ${request.body.isbn}"))
    }

    override suspend fun getBook(request: GetBook.Request): GetBook.Response<*> = library.find(request.path.id)
        ?.let { GetBook.Response200(it) }
        ?: GetBook.Response404(notFound(request.path.id))

    override suspend fun borrowBook(request: BorrowBook.Request): BorrowBook.Response<*> =
        when (val result = library.borrow(request.path.id)) {
            is LoanResult.Ok -> BorrowBook.Response200(result.book)
            LoanResult.NotFound -> BorrowBook.Response404(notFound(request.path.id))
            LoanResult.Conflict -> BorrowBook.Response409(ApiError(code = 409, message = "Book ${request.path.id} is already on loan"))
        }

    override suspend fun returnBook(request: ReturnBook.Request): ReturnBook.Response<*> =
        when (val result = library.giveBack(request.path.id)) {
            is LoanResult.Ok -> ReturnBook.Response200(result.book)
            LoanResult.NotFound -> ReturnBook.Response404(notFound(request.path.id))
            LoanResult.Conflict -> ReturnBook.Response409(ApiError(code = 409, message = "Book ${request.path.id} is not on loan"))
        }

    private fun notFound(id: BookId) = ApiError(code = 404, message = "Book $id not found")
}

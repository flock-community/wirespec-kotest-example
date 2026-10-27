package community.flock.library

import community.flock.library.contract.model.Book
import community.flock.library.contract.model.BookId
import community.flock.library.contract.model.BookInput
import community.flock.library.contract.model.Genre
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class Library {
    private val shelf = ConcurrentHashMap<BookId, Book>()

    fun add(input: BookInput): Book = Book(
        id = BookId(UUID.randomUUID().toString()),
        isbn = input.isbn,
        title = input.title,
        author = input.author,
        genre = input.genre,
        available = true,
    ).also { shelf[it.id] = it }

    fun find(id: BookId): Book? = shelf[id]

    fun list(genre: Genre?): List<Book> = shelf.values.filter { genre == null || it.genre == genre }

    fun borrow(id: BookId): LoanResult = changeAvailability(id, available = false)

    fun giveBack(id: BookId): LoanResult = changeAvailability(id, available = true)

    private fun changeAvailability(id: BookId, available: Boolean): LoanResult = synchronized(shelf) {
        when (val book = shelf[id]) {
            null -> LoanResult.NotFound
            else -> when (book.available) {
                available -> LoanResult.Conflict
                else -> LoanResult.Ok(book.copy(available = available).also { shelf[id] = it })
            }
        }
    }
}

sealed interface LoanResult {
    data class Ok(val book: Book) : LoanResult
    data object NotFound : LoanResult
    data object Conflict : LoanResult
}

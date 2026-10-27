/*
  Identifies a book in the library, a UUID
*/
type BookId = String(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/g)

/*
  A 13-digit ISBN, e.g. 9780441013593
*/
type Isbn = String(/^97[89][0-9]{10}$/g)

enum Genre {
  FICTION, NON_FICTION, SCIENCE, CHILDREN
}

/*
  What a librarian provides to put a new book on the shelf
*/
type BookInput {
  isbn: Isbn,
  title: String,
  author: String,
  genre: Genre
}

/*
  A book on the shelf
*/
type Book {
  id: BookId,
  isbn: Isbn,
  title: String,
  author: String,
  genre: Genre,
  available: Boolean
}

/*
  How readers rate a book on the external review platform
*/
type Rating {
  bookId: BookId,
  reviews: Integer,
  average: Number?
}

/*
  Returned when a call fails
*/
type ApiError {
  code: Integer,
  message: String
}

/*
  Books donated to the library; every donation is put on the shelf
*/
channel BookDonations -> BookInput

/*
  All books, optionally only those of one genre
*/
endpoint ListBooks GET /books ? {genre: Genre?} -> {
  200 -> Book[]
}

/*
  Put a new book on the shelf
*/
endpoint AddBook POST BookInput /books -> {
  201 -> Book
  400 -> ApiError
}

endpoint GetBook GET /books/{id: BookId} -> {
  200 -> Book
  404 -> ApiError
}

/*
  Borrow a book; fails with 409 when it is already on loan
*/
endpoint BorrowBook POST /books/{id: BookId}/loan -> {
  200 -> Book
  404 -> ApiError
  409 -> ApiError
}

/*
  Bring a borrowed book back; fails with 409 when it was not on loan
*/
endpoint ReturnBook DELETE /books/{id: BookId}/loan -> {
  200 -> Book
  404 -> ApiError
  409 -> ApiError
}

/*
  The rating of a book, based on its reviews on the external review platform
*/
endpoint GetRating GET /books/{id: BookId}/rating -> {
  200 -> Rating
  404 -> ApiError
}

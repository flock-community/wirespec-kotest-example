/*
  One reader's review of a book on the external review platform.
  This file is the platform's contract: the library calls it, it does not implement it.
*/
type Review {
  isbn: String,
  stars: Integer(1, 5),
  text: String
}

/*
  All reviews of the book with this ISBN
*/
endpoint GetReviews GET /reviews ? {isbn: String} -> {
  200 -> Review[]
}

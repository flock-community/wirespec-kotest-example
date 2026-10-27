package community.flock.library

import community.flock.library.contract.endpoint.GetRating
import community.flock.library.contract.model.ApiError
import community.flock.library.contract.model.BookId
import community.flock.library.contract.model.Rating
import community.flock.library.contract.model.Review
import org.springframework.web.bind.annotation.RestController

@RestController
class RatingController(
    private val library: Library,
    private val reviews: ReviewsClient,
) : GetRating.Handler {

    override suspend fun getRating(request: GetRating.Request): GetRating.Response<*> = library.find(request.path.id)
        ?.let { book -> GetRating.Response200(reviews.reviewsOf(book.isbn).toRating(book.id)) }
        ?: GetRating.Response404(ApiError(code = 404, message = "Book ${request.path.id} not found"))
}

private fun List<Review>.toRating(bookId: BookId) = Rating(
    bookId = bookId,
    reviews = size.toLong(),
    average = takeIf { isNotEmpty() }?.map { it.stars }?.average(),
)

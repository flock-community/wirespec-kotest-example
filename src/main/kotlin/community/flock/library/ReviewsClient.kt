package community.flock.library

import community.flock.library.contract.client.GetReviewsClient
import community.flock.library.contract.endpoint.GetReviews
import community.flock.library.contract.model.Isbn
import community.flock.library.contract.model.Review
import community.flock.wirespec.integration.jvm.transport.HttpTransportation
import community.flock.wirespec.kotlin.Wirespec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Calls the external review platform through the client generated from reviews.ws. */
@Component
class ReviewsClient(
    serialization: Wirespec.Serialization,
    @Value("\${reviews.base-url}") baseUrl: String,
) {
    private val client = GetReviewsClient(serialization, HttpTransportation(baseUrl))

    suspend fun reviewsOf(isbn: Isbn): List<Review> = when (val response = client.getReviews(isbn.value)) {
        is GetReviews.Response200 -> response.body
    }
}

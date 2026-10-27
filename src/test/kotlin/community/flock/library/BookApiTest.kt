package community.flock.library

import com.jayway.jsonpath.JsonPath
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = RANDOM_PORT)
class BookApiTest(rest: TestRestTemplate) :
    FunSpec({

        fun addBook(): String {
            val json = """
                {
                  "isbn": "9780441013593",
                  "title": "Dune",
                  "author": "Frank Herbert",
                  "genre": "FICTION"
                }
            """.trimIndent()
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val response = rest.postForEntity("/books", HttpEntity(json, headers), String::class.java)

            response.statusCode shouldBe HttpStatus.CREATED
            return JsonPath.read(response.body, "$.id")
        }

        test("a book can be added and fetched back") {
            val id = addBook()

            val response = rest.getForEntity("/books/$id", String::class.java)

            response.statusCode shouldBe HttpStatus.OK
            JsonPath.read<String>(response.body, "$.title") shouldBe "Dune"
            JsonPath.read<Boolean>(response.body, "$.available") shouldBe true
        }

        test("a book cannot be borrowed twice") {
            val id = addBook()

            rest.postForEntity("/books/$id/loan", null, String::class.java).statusCode shouldBe HttpStatus.OK
            rest.postForEntity("/books/$id/loan", null, String::class.java).statusCode shouldBe HttpStatus.CONFLICT
        }
    })

# From a Wirespec contract to a Kotest-tested Spring Boot service

This repository goes from **one Wirespec file** to a **Spring Boot service with Kotest
integration tests**, one commit per step. It was made to accompany a talk about the
[Wirespec Kotest integration](https://wirespec.io/docs/integration/kotest), so every
step is small enough to show on a slide and is tagged, so you can jump to it.

[Wirespec](https://wirespec.io) is a contract-first language for HTTP endpoints and async
channels. From one `.ws` file it generates models, endpoint definitions, Spring handler
interfaces, clients and, with the Kotest integration, **a typed test DSL** built on Kotest
generators:

```kotlin
val response = AddBook.generate.request {
    body { title("Dune") }        // pin what this scenario is about, generate the rest
}.call()                          // real HTTP call, response checked against the contract

response.shouldBeInstanceOf<AddBook.Response201>()
```

## The steps

| Step | Tag                                                                                   | What happens                                       | Diff                                                                                                   |
|-----:|---------------------------------------------------------------------------------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------------|
|    1 | [`step-01`](https://github.com/flock-community/wirespec-kotest-example/tree/step-01) | Write the contract                                 | –                                                                                                      |
|    2 | [`step-02`](https://github.com/flock-community/wirespec-kotest-example/tree/step-02) | Generate Kotlin with the Gradle plugin             | [01 → 02](https://github.com/flock-community/wirespec-kotest-example/compare/step-01...step-02) |
|    3 | [`step-03`](https://github.com/flock-community/wirespec-kotest-example/tree/step-03) | Turn it into a Spring Boot application             | [02 → 03](https://github.com/flock-community/wirespec-kotest-example/compare/step-02...step-03) |
|    4 | [`step-04`](https://github.com/flock-community/wirespec-kotest-example/tree/step-04) | Implement the generated handlers                   | [03 → 04](https://github.com/flock-community/wirespec-kotest-example/compare/step-03...step-04) |
|    5 | [`step-05`](https://github.com/flock-community/wirespec-kotest-example/tree/step-05) | A first Kotest integration test, the classic way   | [04 → 05](https://github.com/flock-community/wirespec-kotest-example/compare/step-04...step-05) |
|    6 | [`step-06`](https://github.com/flock-community/wirespec-kotest-example/tree/step-06) | Generate the Kotest DSL                            | [05 → 06](https://github.com/flock-community/wirespec-kotest-example/compare/step-05...step-06) |
|    7 | [`step-07`](https://github.com/flock-community/wirespec-kotest-example/tree/step-07) | Scenario tests over real HTTP                      | [06 → 07](https://github.com/flock-community/wirespec-kotest-example/compare/step-06...step-07) |
|    8 | [`step-08`](https://github.com/flock-community/wirespec-kotest-example/tree/step-08) | Property-based integration tests                   | [07 → 08](https://github.com/flock-community/wirespec-kotest-example/compare/step-07...step-08) |
|    9 | [`step-09`](https://github.com/flock-community/wirespec-kotest-example/tree/step-09) | Mock a downstream service from its contract        | [08 → 09](https://github.com/flock-community/wirespec-kotest-example/compare/step-08...step-09) |
|   10 | [`step-10`](https://github.com/flock-community/wirespec-kotest-example/tree/step-10) | Test an async Kafka channel                        | [09 → 10](https://github.com/flock-community/wirespec-kotest-example/compare/step-09...step-10) |

To follow along locally:

```shell
git clone https://github.com/flock-community/wirespec-kotest-example.git
cd wirespec-kotest-example
git checkout step-01      # ... up to step-10, or stay on main for the end result
```

## Slides

The slides for the talk introduce the story and walk through every step with its code:
**[flock-community.github.io/wirespec-kotest-example](https://flock-community.github.io/wirespec-kotest-example/)**

They are a [reveal.js](https://revealjs.com) deck in the Flock style, in [`slides/`](slides). reveal.js
and the fonts are included in the folder, so the deck also works offline: open
`slides/index.html` in a browser. Press `S` for the speaker notes, `F` for full screen and `O`
for the overview. To get a PDF, add `?print-pdf` to the URL and print the page.

GitHub Pages serves the `gh-pages` branch, which holds a copy of `slides/`. After changing the
slides on `main`, publish them with:

```shell
git subtree push --prefix slides origin gh-pages
```

---

## Step 1: Write the contract

Everything starts with [`src/main/wirespec/library.ws`](src/main/wirespec/library.ws), a
small library service. There is no code yet, only the contract:

```wirespec
type Isbn = String(/^97[89][0-9]{10}$/g)

enum Genre {
  FICTION, NON_FICTION, SCIENCE, CHILDREN
}

type BookInput {
  isbn: Isbn,
  title: String,
  author: String,
  genre: Genre
}

endpoint AddBook POST BookInput /books -> {
  201 -> Book
  400 -> ApiError
}

endpoint BorrowBook POST /books/{id: BookId}/loan -> {
  200 -> Book
  404 -> ApiError
  409 -> ApiError
}
```

The full file declares:

- **Refined types**: `BookId` (a UUID) and `Isbn`, each a `String` constrained by a regex.
- **An enum**: `Genre`.
- **Records**: `BookInput`, `Book` and `ApiError`.
- **Five endpoints**: `ListBooks`, `AddBook`, `GetBook`, `BorrowBook` and `ReturnBook`. Between
  them they use path parameters, an optional query parameter, a request body and several
  response statuses per endpoint.

Everything else is generated from this file: the production code, a client and, from step 6
on, the test DSL. The regexes and the declared statuses also come back later. The test
generators produce values that match the regexes, and every response a test receives is
checked against the statuses declared here.

## Step 2: Generate Kotlin with the Gradle plugin

A plain Kotlin/JVM Gradle build plus the Wirespec Gradle plugin. The plugin provides the
`CompileWirespecTask`; [`build.gradle.kts`](build.gradle.kts) registers one and adds its
output to the main source set:

```kotlin
val wirespec = tasks.register<CompileWirespecTask>("wirespec") {
    input = layout.projectDirectory.dir("src/main/wirespec")
    output = layout.buildDirectory.dir("generated/wirespec")
    packageName = "community.flock.library.contract"
    languages = listOf(Language.Kotlin)
}

kotlin.sourceSets.main {
    kotlin.srcDir(wirespec.flatMap { it.output })
}
```

Because the source directory is the task's output, Gradle runs `wirespec` before
`compileKotlin` without an explicit `dependsOn`. Run it with:

```shell
./gradlew build
```

and look in `build/generated/wirespec/community/flock/library/contract`:

| Package      | Contains                                                                                                    |
|--------------|-------------------------------------------------------------------------------------------------------------|
| `model`      | `data class`es and `enum`s; refined types are wrappers with a `validate()` that checks the regex            |
| `endpoint`   | One `object` per endpoint: `Request`, a sealed `Response` per status, a `Handler` interface, (de)serializers |
| `client`     | A client per endpoint and a `Client` for all of them, on top of a pluggable `Wirespec.Transportation`        |
| `generator`  | A `<Type>Generator` per type that builds values from a `Wirespec.Generator`; Kotest provides one in step 6  |

The generated code only depends on the small Wirespec runtime (`wirespec-jvm`), which holds the
interfaces it implements, such as `Wirespec.Endpoint` and `Wirespec.Refined`.

All versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). This project uses
Wirespec `0.22.5`, Kotlin `2.3.21`, Gradle `9.7.1` and a Java 21 toolchain. The foojay resolver
in [`settings.gradle.kts`](settings.gradle.kts) downloads that JDK if it is not installed.

## Step 3: Turn it into a Spring Boot application

Three additions turn the build into a Spring Boot application:

1. **Plugins and dependencies**: the Spring Boot and `kotlin-spring` plugins, the Spring Boot BOM as a
   Gradle `platform(...)`, `spring-boot-starter-web`, `kotlinx-coroutines-reactor` (the generated
   handlers are `suspend` functions) and the
   [Wirespec Spring integration](https://wirespec.io/docs/integration/spring) (`spring-jvm`).
2. **An IR extension on the compile task**: `SpringMappingAnnotationsExtension` adds Spring's mapping
   annotations to every generated `Handler` interface:

   ```kotlin
   tasks.register<CompileWirespecTask>("wirespec") {
       // ...
       extensionClasses = listOf(SpringMappingAnnotationsExtension::class.java)
   }
   ```

   ```kotlin
   // build/generated/.../endpoint/AddBook.kt
   interface Handler : Wirespec.Handler {
       @org.springframework.web.bind.annotation.PostMapping("/books")
       suspend fun addBook(request: Request): Response<*>
   }
   ```

   The extension ships with the Gradle plugin, so the build script needs no extra `buildscript`
   classpath entries.
3. **The application class**: [`LibraryApplication`](src/main/kotlin/community/flock/library/LibraryApplication.kt).
   `@EnableWirespecController` registers the argument resolver that turns an HTTP request into the
   generated `Request` type. It also registers a `Wirespec.Serialization` bean, which the tests use
   from step 7 on.

`./gradlew bootRun` starts the service. No handler is implemented yet, so every endpoint still
answers `404`.

> The Spring Boot BOM is imported with Gradle's `platform(...)`, not with the
> `io.spring.dependency-management` plugin. A platform only adds version constraints and lets Gradle
> resolve conflicts as usual. The dependency-management plugin enforces the BOM versions instead,
> and would downgrade for example `kotlinx-coroutines` below the version Kotest and Wirespec need.

## Step 4: Implement the generated handlers

[`BookController`](src/main/kotlin/community/flock/library/BookController.kt) implements the five
generated `Handler` interfaces, and [`Library`](src/main/kotlin/community/flock/library/Library.kt)
keeps the books in memory:

```kotlin
@RestController
class BookController(private val library: Library) :
    ListBooks.Handler, AddBook.Handler, GetBook.Handler, BorrowBook.Handler, ReturnBook.Handler {

    override suspend fun addBook(request: AddBook.Request): AddBook.Response<*> = when {
        request.body.isbn.validate() -> AddBook.Response201(library.add(request.body))
        else -> AddBook.Response400(ApiError(code = 400, message = "Invalid ISBN: ${request.body.isbn}"))
    }

    override suspend fun borrowBook(request: BorrowBook.Request): BorrowBook.Response<*> =
        when (val result = library.borrow(request.path.id)) {
            is LoanResult.Ok -> BorrowBook.Response200(result.book)
            LoanResult.NotFound -> BorrowBook.Response404(notFound(request.path.id))
            LoanResult.Conflict -> BorrowBook.Response409(ApiError(code = 409, message = "..."))
        }
    // ...
}
```

The routing comes from the generated annotations, so the controller contains no `@RequestMapping`.
The return type also follows the contract: a handler can only return the variants its endpoint
declares, so `AddBook` cannot answer with a `404`. The ISBN check calls the `validate()` that
Wirespec generated from the regex in `library.ws`.

Try it:

```shell
./gradlew bootRun

curl -i -X POST localhost:8080/books -H 'Content-Type: application/json' \
  -d '{"isbn":"9780441013593","title":"Dune","author":"Frank Herbert","genre":"FICTION"}'
curl -i 'localhost:8080/books?genre=FICTION'
curl -i -X POST localhost:8080/books/<id>/loan     # 200, then 409 the second time
```

## Step 5: A first Kotest integration test, the classic way

Add Kotest (`kotest-runner-junit5`, `kotest-assertions-core`), the
[Kotest Spring extension](https://kotest.io/docs/extensions/spring.html) and
`spring-boot-starter-test`, and register the extension once for the whole test suite in
[`ProjectConfig`](src/test/kotlin/community/flock/library/ProjectConfig.kt):

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(SpringExtension())
}
```

[`kotest.properties`](src/test/resources/kotest.properties) tells Kotest where that class is
(`kotest.framework.config.fqn`). This works for Gradle and for the IntelliJ Kotest plugin alike.

[`BookApiTest`](src/test/kotlin/community/flock/library/BookApiTest.kt) is written the way many
Spring Boot integration tests are: a `TestRestTemplate`, JSON in a string and JsonPath to read the
answer.

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class BookApiTest(rest: TestRestTemplate) : FunSpec({

    fun addBook(): String {
        val json = """{ "isbn": "9780441013593", "title": "Dune", "author": "Frank Herbert", "genre": "FICTION" }"""
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.postForEntity("/books", HttpEntity(json, headers), String::class.java)
        response.statusCode shouldBe HttpStatus.CREATED
        return JsonPath.read(response.body, "$.id")
    }

    test("a book can be added and fetched back") {
        val response = rest.getForEntity("/books/${addBook()}", String::class.java)
        response.statusCode shouldBe HttpStatus.OK
        JsonPath.read<String>(response.body, "$.title") shouldBe "Dune"
    }
})
```

It works, but nothing in it is connected to the contract we started with:

- The request body is a hand-written string. Rename a field in `library.ws` and the test still
  compiles, but now sends the wrong JSON.
- Every test needs a complete, valid book, even when it only cares about one field, and every test
  uses the same Dune.
- The response is read with untyped JsonPath strings, and nothing checks that a status code or body
  is one the contract declares.

The next steps solve these with code generated from the same contract.

## Step 6: Generate the Kotest DSL

One more IR extension on the compile task generates the test DSL:

```kotlin
extensionClasses = listOf(
    SpringMappingAnnotationsExtension::class.java,
    KotestDslExtension::class.java,
)
```

`KotestDslExtension` writes one `<Name>Dsl.kt` per type and per endpoint into the
`community.flock.library.contract.kotest` package:

| Generated                               | What it gives you                                                                      |
|-----------------------------------------|----------------------------------------------------------------------------------------|
| `BookInput.generate { ... }`            | an `Arb<BookInput>`: random but valid books, pinning only the fields you set           |
| `AddBook.generate.request { ... }`      | an `Arb<AddBook.Request>` with `path { }`, `query { }` and `body { }` blocks             |
| `AddBook.generate.response201 { ... }`  | an `Arb` per declared response, for stubs (step 9)                                      |
| `Gen<AddBook.Request>.call()`           | sends one generated request and returns the typed response (step 7)                    |
| `Gen<AddBook.Response<*>>.mock { ... }` | stubs a downstream service with a generated response (step 9)                          |

Each builder has a field slot per field. The function form pins a constant and the property form takes
a Kotest `Arb`:

```kotlin
BookInput.generate {
    title("Dune")                          // a fixed value
    genre = Arb.enum<Genre>()              // a random genre on every draw
}
```

To make `BookInput.generate` possible, the extension adds an empty `companion object` to each
generated record. The models themselves stay free of Kotest.

### Keep Kotest out of the production jar

The DSL is generated next to the models, so by default it compiles with `main`, and `kotest-property`
and `kotest-jvm` become `implementation` dependencies. This build moves the `kotest` package to the
test source set instead:

```kotlin
val wirespecKotestDsl = tasks.register<Sync>("wirespecKotestDsl") {
    from(wirespec.flatMap { it.output }) { include("**/contract/kotest/**") }
    into(layout.buildDirectory.dir("generated/wirespec-kotest"))
}

kotlin.sourceSets {
    main {
        kotlin.srcDir(wirespec.flatMap { it.output })
        kotlin.exclude("**/contract/kotest/**")
    }
    test {
        kotlin.srcDir(wirespecKotestDsl)
    }
}
```

With this, `kotest-property` and the Wirespec Kotest runtime (`kotest-jvm`) are `testImplementation`
dependencies. If you prefer fewer lines in the build script over a smaller jar, skip the `Sync` task
and declare both as `implementation`.

### Generated data, without Spring

[`GeneratedDataTest`](src/test/kotlin/community/flock/library/GeneratedDataTest.kt) is a plain Kotest
spec with no Spring context and no HTTP:

```kotlin
test("every generated book satisfies the contract") {
    checkAll(BookInput.generate()) { book ->
        book.isbn.validate() shouldBe true          // the regex from library.ws
    }
}

test("pin the fields a test is about, generate the rest") {
    val book = BookInput.generate { title("Dune"); genre(Genre.FICTION) }.draw()
    book.title shouldBe "Dune"
}

test("the same seed generates the same data") {
    val first = BookInputGenerator.generate(kotestWirespecKotlinGenerator(seed = 42), emptyList())
    val second = BookInputGenerator.generate(kotestWirespecKotlinGenerator(seed = 42), emptyList())
    first shouldBe second
}
```

The generated values follow the contract. Refined types like the ISBN are generated with a reverse
regex: [rgxgen](https://github.com/flock-community/kotlin-rgxgen) runs the regex from `library.ws`
backwards and produces strings that match it. Enums are drawn from their entries, and strings get
readable values that start with the field name:

```text
BookInput(isbn=9797433818565, title=title-Y4xwT7n9, author=author-YJMi5uf3, genre=NON_FICTION)
BookInput(isbn=9799713690494, title=title-G5fNapuj, author=author-PQxB_RLA, genre=CHILDREN)
```

The last test uses the lower-level API. `kotestWirespecKotlinGenerator(seed)` is a
`Wirespec.Generator` backed by Kotest `Arb`s, and it drives the `<Type>Generator`s from step 2. The
DSL is built on top of it.

## Step 7: Scenario tests over real HTTP

The DSL needs to know how to reach the application. That is what `WirespecEndpointExtension` is for.
[`LibraryApi.kt`](src/test/kotlin/community/flock/library/LibraryApi.kt) creates one that reads
everything from the Spring test context:

```kotlin
val libraryApi = WirespecEndpointExtension(
    serialization = { serialization() },
    transportation = { HttpTransportation("http://localhost:${property("local.server.port")}") },
)

suspend fun serialization(): Wirespec.Serialization = applicationContext().getBean<Wirespec.Serialization>()
```

- The **serialization** is the application's own `Wirespec.Serialization` bean, so the tests write and
  read JSON exactly like the service does.
- The **transportation** is `HttpTransportation` from the Wirespec JVM integration (`jvm-jvm`), a
  `Wirespec.Transportation` on the JDK `HttpClient`, pointed at the random port of `@SpringBootTest`.

A spec that talks to the application registers it with `extension(libraryApi)`. Here is
[`BookApiTest`](src/test/kotlin/community/flock/library/BookApiTest.kt) from step 5, rewritten with
the DSL:

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class BookApiTest : FunSpec({
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
    }

    test("an invalid ISBN is rejected") {
        AddBook.generate.request { body { isbn("not-an-isbn") } }.call()
            .shouldBeInstanceOf<AddBook.Response400>()
    }
})
```

Compared to step 5:

- **No JSON, no URLs, no JsonPath.** The request is typed and built from the contract. If a field is
  renamed in `library.ws`, the test no longer compiles.
- **Only what the scenario is about is spelled out.** The test pins the title, and the ISBN, author
  and genre are generated, with valid values. To test the unhappy path, pin an invalid value on
  purpose: `isbn("not-an-isbn")`.
- **Typed responses.** `call()` returns the endpoint's sealed `Response`. `shouldBeInstanceOf` narrows
  it, and from there `added.body.id` is a `BookId`.
- **Every response is checked against the contract.** A status the contract does not declare, or a
  body that does not deserialize into the declared type, fails the test even if the assertions
  would pass. For example, when `GetBook` throws instead of returning a `404`:

  ```text
  java.lang.AssertionError: GetBook failed (wirespec seed=1872276388795): [GetBook] UndeclaredStatus:
    status 500 is not declared by the contract. Declared: [200, 404]
    body={"timestamp":"...","status":500,"error":"Internal Server Error","path":"/books/7c722694-..."}
  ```

> Why `extension(libraryApi)` in the spec and not in `ProjectConfig`? The extension sets up its
> serialization and transport before every test of every spec it applies to, and it reads both from
> the Spring context. The Spring-free `GeneratedDataTest` has no Spring context. If every spec in your suite is a
> `@SpringBootTest`, you can register it once in `ProjectConfig` next to `SpringExtension()`.

## Step 8: Property-based integration tests

Every builder slot takes a value or a Kotest `Arb`, and every entry point returns an `Arb`. That makes
Kotest's [property testing](https://kotest.io/docs/proptest/property-based-testing.html) (`checkAll`
with an `Arb` or `Exhaustive`) available for integration tests.
[`BookPropertiesTest`](src/test/kotlin/community/flock/library/BookPropertiesTest.kt) has three
properties:

```kotlin
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
    val text = Arb.string(1..40, Arb.choice(Codepoint.printableAscii(), Codepoint.cyrillic(), Codepoint.egyptianHieroglyphs()))

    checkAll(50, text, text) { anyTitle, anyAuthor ->
        val book = addBook { title(anyTitle); author(anyAuthor) }
        GetBook.generate.request { path { id(book.id) } }.call()
            .shouldBeInstanceOf<GetBook.Response200>()
            .body shouldBe book
    }
}
```

The third one is a small **stateful** property. It generates a random sequence of `BORROW` and
`RETURN` actions, sends them to the service and checks each answer against a one-line model: you can
borrow when the book is available and return when it is not.

```kotlin
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
```

Each iteration adds its own book, so iterations do not share state, and Kotest can **shrink** a
failure against the running service. Break the conflict check in `Library` so a book can be borrowed
twice, and the property fails with the smallest sequence that shows it:

```text
Property failed after 3 attempts

    Arg 0: [BORROW, BORROW] (shrunk from [RETURN, RETURN, RETURN, RETURN, RETURN, RETURN, BORROW, BORROW])

Repeat this test by using seed 3571094219167116827
```

## Step 9: Mock a downstream service from its contract

The library now shows how readers rate a book. Ratings come from an external review platform, which
publishes its own Wirespec contract, [`reviews.ws`](src/main/wirespec/reviews.ws):

```wirespec
type Review {
  isbn: String,
  stars: Integer(1, 5),
  text: String
}

endpoint GetReviews GET /reviews ? {isbn: String} -> {
  200 -> Review[]
}
```

It sits next to `library.ws`, so the same task generates its models, its client and its test DSL.
`library.ws` gets a `Rating` type and a `GetRating GET /books/{id}/rating` endpoint.
[`ReviewsClient`](src/main/kotlin/community/flock/library/ReviewsClient.kt) calls the platform
through the generated `GetReviewsClient`, and
[`RatingController`](src/main/kotlin/community/flock/library/RatingController.kt) turns the reviews
into a `Rating`.

### Mocking with `.mock { }`

`.mock { }` is the counterpart of `.call()`. `call()` generates a request and *sends* it, and
`mock { }` generates a response and *stubs* it on a mock server. The stub answers every request that
the typed predicate accepts:

```kotlin
GetReviews.generate.response200 { body(reviews) }
    .mock { request -> request.queries.isbn == book.isbn.value }   // request is a typed GetReviews.Request
```

The mock server comes from `WirespecMockExtension`. It works with any `MockServer`, a two-method
interface (`stub`, `reset`).
[`WireMockMockServer`](src/test/kotlin/community/flock/library/WireMockMockServer.kt) implements it in
about 30 lines on top of WireMock, using the helpers from the Wirespec WireMock integration
(`wiremock-jvm`) that turn a method, a path template and a raw response into a WireMock mapping.

[`RatingTest`](src/test/kotlin/community/flock/library/RatingTest.kt) starts the server before the
Spring context, so the application's `reviews.base-url` can point at it:

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class RatingTest : FunSpec({
    extension(libraryApi)
    extension(WirespecMockExtension(server = reviewService, serialization = { serialization() }))

    test("the rating is the average of the book's reviews") {
        val book = addBook()
        val reviews = listOf(5L, 4L, 3L).map { stars ->
            Review.generate { isbn(book.isbn.value); stars(stars) }.draw()
        }
        GetReviews.generate.response200 { body(reviews) }
            .mock { request -> request.queries.isbn == book.isbn.value }

        ratingOf(book) shouldBe Rating(bookId = book.id, reviews = 3, average = 4.0)
    }
}) {
    companion object {
        private val reviewService = WireMockMockServer.start()

        @JvmStatic
        @DynamicPropertySource
        fun reviewsBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("reviews.base-url") { reviewService.baseUrl }
        }
    }
}
```

The downstream contract is useful in a property too. `Review.generate()` produces reviews the
platform could really send, with `stars` between 1 and 5 as `Integer(1, 5)` requires. So the test
can check that the library handles every such answer:

```kotlin
test("whatever the review platform answers, the average stays within the stars its contract allows") {
    checkAll(20, Arb.list(Review.generate(), 1..25)) { reviews ->
        val book = addBook()
        GetReviews.generate.response200 { body(reviews) }
            .mock { request -> request.queries.isbn == book.isbn.value }

        val rating = ratingOf(book)
        rating.average.shouldNotBeNull() shouldBeIn 1.0..5.0
        rating.average shouldBe (reviews.map { it.stars }.average() plusOrMinus 1e-9)
    }
}
```

The response is (de)serialized with the application's own `Wirespec.Serialization`, so the stub
sends exactly the JSON the generated client expects. If the application calls the platform in a way
no stub accepts, WireMock answers `404`. The `GetReviews` contract does not declare that status, so the
generated client throws, the application answers `500`, and the test fails with a contract violation:

```text
java.lang.AssertionError: GetRating failed (wirespec seed=2410745794640): [GetRating] UndeclaredStatus:
  status 500 is not declared by the contract. Declared: [200, 404]
```

## Step 10: Test an async Kafka channel

Wirespec also describes asynchronous messaging. People can now donate books, and every donation
arrives on a Kafka topic:

```wirespec
channel BookDonations -> BookInput
```

[`DonationListener`](src/main/kotlin/community/flock/library/DonationListener.kt) consumes the
`book-donations` topic with a plain `@KafkaListener`. It deserializes each message with the
application's `Wirespec.Serialization` and puts every donation with a valid ISBN on the shelf.

On the test side, `KotestDslExtension` generates `BookDonations.generate.message { ... }`, which
returns a `Gen<BookInput>`, and `.send(topic)`. Sending goes through `WirespecChannelExtension`.
Like the mock extension, it works with any transport, here a one-method `ChannelTransport`.
[`KafkaChannelTransport`](src/test/kotlin/community/flock/library/KafkaChannelTransport.kt)
implements it with a plain `KafkaProducer`.

[`DonationTest`](src/test/kotlin/community/flock/library/DonationTest.kt) runs against an in-JVM broker
(`@EmbeddedKafka`) and combines the channel DSL and the endpoint DSL with Kotest's `eventually`:

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["spring.kafka.listener.auto-startup=true"])
@EmbeddedKafka(partitions = 1, topics = [BOOK_DONATIONS_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class DonationTest : FunSpec({
    extension(libraryApi)
    extension(
        WirespecChannelExtension(
            serialization = { serialization() },
            transportation = { KafkaChannelTransport(property("spring.kafka.bootstrap-servers")) },
        ),
    )

    test("a donated book ends up on the shelf") {
        val donation = BookDonations.generate.message { title("The Hobbit") }.send(BOOK_DONATIONS_TOPIC)

        eventually(15.seconds) {
            shelf() shouldContain donation         // shelf() lists the books with ListBooks ... .call()
        }
    }

    test("a donation with an invalid ISBN is not put on the shelf") {
        val invalid = BookDonations.generate.message { isbn("not-an-isbn") }.send(BOOK_DONATIONS_TOPIC)
        val valid = BookDonations.generate.message { }.send(BOOK_DONATIONS_TOPIC)

        eventually(15.seconds) { shelf() shouldContain valid }
        shelf() shouldNotContain invalid
    }
})
```

`send()` returns the message it generated and published, so the test can assert on it afterwards. The
channel DSL only covers the sending side. To check messages that the application itself publishes,
read the topic with a regular Kafka consumer in the test.

Only `DonationTest` needs a broker. [`src/test/resources/config/application.yaml`](src/test/resources/config/application.yaml)
is loaded on top of the application's own configuration in every test. It turns the Kafka listeners
off, so the other specs do not try to reach `localhost:9092`. `DonationTest` turns them back on.

---

## Recap

One contract, four entry points into the generated DSL:

| You write                                         | It does                                                     | Backed by                   |
|---------------------------------------------------|-------------------------------------------------------------|-----------------------------|
| `BookInput.generate { title("Dune") }`            | an `Arb` of contract-valid values, pinning what you set     | nothing, plain Kotest       |
| `AddBook.generate.request { ... }.call()`         | sends a generated request, returns the typed response       | `WirespecEndpointExtension` |
| `GetReviews.generate.response200 { ... }.mock { }`| stubs a downstream service with a generated response        | `WirespecMockExtension`     |
| `BookDonations.generate.message { ... }.send()`   | publishes a generated message to a channel                  | `WirespecChannelExtension`  |

Because every entry point is a Kotest `Gen`, they also work with `checkAll`, `Exhaustive`, shrinking
and the rest of Kotest's property testing.

## Running

```shell
./gradlew build      # generate, compile and run all tests
./gradlew test       # only the tests
./gradlew bootRun    # start the service on :8080
```

The tests need neither Docker nor any running service. WireMock and the Kafka broker run inside the
test JVM. `bootRun` expects a Kafka broker for the donations (`KAFKA_BOOTSTRAP_SERVERS`, default
`localhost:9092`) and the review platform for the ratings (`REVIEWS_BASE_URL`, default
`http://localhost:8081`). Without them the other endpoints still work.

Versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml): Wirespec `0.22.5`, Kotest
`6.1.11`, Spring Boot `3.5.16`, Kotlin `2.3.21`, Gradle `9.7.1` and Java 21.

## Further reading

- [Wirespec documentation](https://wirespec.io/docs)
- [Kotest integration](https://wirespec.io/docs/integration/kotest), with more detail in the integration's
  [README](https://github.com/flock-community/wirespec/blob/master/src/integration/kotest/README.md)
- [Wirespec on GitHub](https://github.com/flock-community/wirespec), including
  [`examples/gradle-kotest`](https://github.com/flock-community/wirespec/tree/master/examples/gradle-kotest),
  a larger example with the same integration
- [Kotest property testing](https://kotest.io/docs/proptest/property-based-testing.html)

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

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

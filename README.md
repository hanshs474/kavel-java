# kavel-java

Generate AI images from Java with **no API key, no account and no dependencies**.

```xml
<dependency>
  <groupId>io.github.hanshs474</groupId>
  <artifactId>kavel</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
implementation("io.github.hanshs474:kavel:0.1.0")
```

```java
Kavel.Image img = Kavel.create().generate(
    "matte black ceramic mug on pale oak, soft window light from the left, shallow depth of field",
    "16:9");
img.url();          // https://cdn.kavel.ai/uploads/kie/image/....webp
img.watermarked();  // true on the free tier
```

Every other image client wants a key from OpenAI, fal or Replicate before it runs once. This one
talks to the anonymous tier of [Kavel](https://www.kavel.ai/?utm_source=maven&utm_medium=package),
an online AI image and video studio, with a client id it invents instead of an account you register.
Add the dependency and the first call works on a machine with nothing configured.

## What you get

- **Zero dependencies.** `java.net.http` and the standard library, Java 11 and up. Nothing to pin,
  nothing to shade, nothing that drifts out from under you. Works the same from Kotlin and Scala.
  (Android does not ship `java.net.http`, so this library is for the JVM.)
- **A url, not bytes.** The returned CDN link is permanent and cacheable, and `watermarked()` says
  whether a mark was actually drawn rather than leaving you to assume.
- **Failures you can branch on.** `KavelException.reason()` is `QUOTA`, `REJECTED`, `SIGN_IN`,
  `AUTH` or `TIMEOUT`, so a `switch` tells you whether to wait, reword, or sign in.
- **It keeps polling through a dropped connection.** A free run waits in a queue and only starts
  when a poll crosses the end of it, so giving up on one failed poll would throw away a job that
  was about to run.
- **Thread-safe.** One `Kavel` instance can serve a whole app.

## The free tier, read live

```java
Kavel.create().credits();   // remaining/grant for a fresh client id, costs nothing
```

On 2026-10-02 a fresh client id was granted **5 credits**, and one generated image spent all five. The
client mints a new id per call. A per-machine daily ceiling sits on top of that (two images from one IP
that day), and hitting it raises `QUOTA`. Free output is 1K and watermarked, and free runs wait in a queue before the model starts
(a test call that day took 72 seconds end to end), so the default deadline is six minutes.

## Editing a photo you already have

```java
Kavel kavel = Kavel.builder().apiKey("your-key").build();   // or set KAVEL_API_KEY
Kavel.Image out = kavel.edit("https://example.com/portrait.jpg",
    "shoulder-length layered haircut, keep the same face, skin and lighting");
```

An edit costs more than the free grant, so it needs a key from
[kavel.ai/settings/apikeys](https://www.kavel.ai/settings/apikeys?utm_source=maven&utm_medium=package).
Without one, `edit` raises `QUOTA` before anything is charged. Naming what must stay is what holds
the likeness. Prompts that only name the change tend to drift the whole face.

With a key every call runs on your account: your credits, no watermark on a paid plan, and
`Kavel.builder().model(...)` picks any image model on your plan.

## Settings

```java
Kavel kavel = Kavel.builder()
    .apiKey(System.getenv("KAVEL_API_KEY"))
    .timeout(Duration.ofMinutes(3))
    .pollEvery(Duration.ofSeconds(3))
    .httpClient(HttpClient.newBuilder().proxy(ProxySelector.of(addr)).build())
    .build();
```

## Try it without a build tool

```bash
curl -LO https://repo1.maven.org/maven2/io/github/hanshs474/kavel/0.1.0/kavel-0.1.0.jar
java -cp kavel-0.1.0.jar example/Generate.java "an isometric coffee shop, pastel palette"
```

## Other languages

The same no-key client exists for
[Python](https://pypi.org/project/kavel/), [Go](https://pkg.go.dev/github.com/hanshs474/kavel-go),
[Rust](https://docs.rs/kavel), [TypeScript](https://jsr.io/@kavel/kavel),
[PHP](https://packagist.org/packages/hanshs474/kavel), [Ruby](https://rubygems.org/gems/kavel),
[Dart](https://pub.dev/packages/kavel), [.NET](https://www.nuget.org/packages/Kavel), and as an
[MCP server](https://github.com/hanshs474/kavel-mcp) for Claude, Cursor and other agents.

## License

MIT. Generated with [Kavel AI](https://www.kavel.ai/?utm_source=maven&utm_medium=package).

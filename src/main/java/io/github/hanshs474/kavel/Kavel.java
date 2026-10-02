package io.github.hanshs474.kavel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Generate and edit images from Java with no API key and no account.
 *
 * <pre>{@code
 * Kavel.Image img = Kavel.create().generate("a paper boat at sunrise, soft fog, 35mm film");
 * System.out.println(img.url());
 * }</pre>
 *
 * <p>Without a key it calls the anonymous tier of <a href="https://www.kavel.ai">kavel.ai</a>,
 * which meters a small free allowance against a client id this class invents
 * rather than an account you register, so a program using it runs on a machine
 * with nothing configured. Read the allowance live with {@link #credits()}.
 *
 * <p>With a key ({@link Builder#apiKey(String)} or the {@code KAVEL_API_KEY}
 * environment variable) every call runs on your account: your credits, your
 * plan's models, and {@link #edit(String, String)} on a photo you already have.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class Kavel {

    /** The free text-to-image engine. It takes no image input. */
    public static final String MODEL_GENERATE = "kavel-image-v1";
    /** The image-to-image engine used by {@link #edit(String, String)}. */
    public static final String MODEL_EDIT = "nano-banana-2-lite";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration pollEvery;
    private final Duration timeout;
    private final HttpClient http;

    private Kavel(Builder b) {
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey != null ? b.apiKey : System.getenv("KAVEL_API_KEY");
        this.model = b.model;
        this.pollEvery = b.pollEvery;
        this.timeout = b.timeout;
        this.http = b.http != null ? b.http
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    /** @return a client with every default: anonymous unless {@code KAVEL_API_KEY} is set */
    public static Kavel create() {
        return builder().build();
    }

    /** @return a builder for a configured client */
    public static Builder builder() {
        return new Builder();
    }

    /** One finished picture. */
    public static final class Image {
        private final String url;
        private final boolean watermarked;

        Image(String url, boolean watermarked) {
            this.url = url;
            this.watermarked = watermarked;
        }

        /** @return a permanent, cacheable CDN link */
        public String url() {
            return url;
        }

        /** @return whether a mark was actually drawn; the free tier draws one, signing in removes it */
        public boolean watermarked() {
            return watermarked;
        }

        @Override
        public String toString() {
            return url + (watermarked ? " (watermarked)" : "");
        }
    }

    /** What a client id has left, read from the same endpoint the site's own header uses. */
    public static final class Credits {
        private final int remaining;
        private final int grant;

        Credits(int remaining, int grant) {
            this.remaining = remaining;
            this.grant = grant;
        }

        /** @return credits left for this client id */
        public int remaining() {
            return remaining;
        }

        /** @return what a fresh client id starts with */
        public int grant() {
            return grant;
        }

        @Override
        public String toString() {
            return remaining + "/" + grant;
        }
    }

    /**
     * Turns a prompt into a new square image.
     *
     * @see #generate(String, String)
     */
    public Image generate(String prompt) throws KavelException {
        return generate(prompt, "1:1");
    }

    /**
     * Turns a prompt into a new image.
     *
     * <p>Naming the light, the material and the composition moves the result
     * far more than adding adjectives: "a product photo of a mug" gives the
     * model nothing, "matte black ceramic mug on pale oak, soft window light
     * from the left, shallow depth of field" gives it a picture.
     *
     * @param prompt      what to draw
     * @param aspectRatio one of {@code 1:1}, {@code 16:9}, {@code 9:16}, {@code 4:3}, {@code 3:4}
     * @return the finished image
     * @throws KavelException with a {@link KavelException.Reason} to branch on
     */
    public Image generate(String prompt, String aspectRatio) throws KavelException {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: prompt is required");
        }
        String ratio = aspectRatio == null || aspectRatio.isEmpty() ? "1:1" : aspectRatio;
        String payload = "{\"provider\":\"kie\",\"mediaType\":\"image\",\"model\":" + Json.quote(pick(MODEL_GENERATE))
                + ",\"scene\":\"text-to-image\",\"prompt\":" + Json.quote(prompt)
                + ",\"options\":{\"aspect_ratio\":" + Json.quote(ratio) + "}}";
        return run(payload);
    }

    /**
     * Rewrites an existing image: "give him a buzz cut", "put the product on a
     * marble surface". Naming what must stay ("keep the same face and
     * lighting") is what holds the likeness.
     *
     * <p>An edit costs more than the anonymous allowance, so without an API key
     * this throws {@link KavelException.Reason#QUOTA}. Signed-out edits run in
     * the browser at <a href="https://www.kavel.ai">kavel.ai</a>.
     *
     * @param sourceUrl   a publicly reachable http(s) url
     * @param instruction what to change
     * @return the edited image
     * @throws KavelException with a {@link KavelException.Reason} to branch on
     */
    public Image edit(String sourceUrl, String instruction) throws KavelException {
        if (sourceUrl == null || !(sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://"))) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: sourceUrl must be a public http(s) url");
        }
        if (instruction == null || instruction.trim().isEmpty()) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: instruction is required");
        }
        String payload = "{\"provider\":\"kie\",\"mediaType\":\"image\",\"model\":" + Json.quote(pick(MODEL_EDIT))
                + ",\"scene\":\"image-to-image\",\"prompt\":" + Json.quote(instruction)
                + ",\"options\":{\"image_input\":[" + Json.quote(sourceUrl) + "]}}";
        return run(payload);
    }

    /**
     * Reports what a fresh anonymous client id is granted. It costs nothing to
     * call, so a program can check before it spends.
     */
    public Credits credits() throws KavelException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/anon-credits"))
                .header("x-anon-id", anonId())
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        Map<String, Object> data = data(send(req));
        return new Credits(num(data.get("remaining")), num(data.get("grant")));
    }

    private String pick(String free) {
        return hasKey() && model != null ? model : free;
    }

    private boolean hasKey() {
        return apiKey != null && !apiKey.isEmpty();
    }

    private HttpRequest.Builder authorize(HttpRequest.Builder b, String id) {
        return hasKey() ? b.header("Authorization", "Bearer " + apiKey) : b.header("x-anon-id", id);
    }

    private Image run(String payload) throws KavelException {
        Instant deadline = Instant.now().plus(timeout);
        // With a key the account is the identity. Without one, a fresh id per
        // call, so a reused id does not wall partway through a loop for reasons
        // the caller cannot see.
        String id = anonId();
        HttpRequest submit = authorize(HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/generate")), id)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        Map<String, Object> submitted = data(send(submit));

        // The quota wall answers HTTP 200 with code 0 and wall:true.
        if (Boolean.TRUE.equals(submitted.get("wall"))) {
            if ("anon_unmetered_video".equals(submitted.get("reason"))) {
                throw new KavelException(KavelException.Reason.SIGN_IN, "kavel: this needs a signed-in account");
            }
            String why = "anon_ip_daily".equals(submitted.get("reason"))
                    ? "this machine has used its free credits for today"
                    : "the free allowance does not cover this run";
            throw new KavelException(KavelException.Reason.QUOTA, "kavel: " + why
                    + " (create an API key at https://www.kavel.ai/settings/apikeys to keep going)");
        }
        Object taskId = submitted.get("id");
        if (!(taskId instanceof String) || ((String) taskId).isEmpty()) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: the service returned no task id");
        }
        String task = (String) taskId;

        // Free runs wait in a queue and are only sent to the model by the poll
        // that crosses the end of it, so polling is what starts the work.
        IOException lastError = null;
        while (true) {
            if (Instant.now().isAfter(deadline)) {
                throw new KavelException(KavelException.Reason.TIMEOUT,
                        "kavel: no image before the deadline" + (lastError != null ? " (last poll: " + lastError.getMessage() + ")" : ""));
            }
            try {
                Thread.sleep(pollEvery.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KavelException(KavelException.Reason.OTHER, "kavel: interrupted", e);
            }
            HttpRequest poll;
            if (hasKey()) {
                poll = authorize(HttpRequest.newBuilder(URI.create(baseUrl + "/api/ai/query")), id)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"taskId\":" + Json.quote(task) + "}")).build();
            } else {
                String q = baseUrl + "/api/ai/anon-query?taskId=" + URLEncoder.encode(task, StandardCharsets.UTF_8)
                        + "&provider=kie&mediaType=image";
                poll = authorize(HttpRequest.newBuilder(URI.create(q)), id).timeout(Duration.ofSeconds(30)).GET().build();
            }
            String body;
            try {
                body = http.send(poll, HttpResponse.BodyHandlers.ofString()).body();
            } catch (IOException e) {
                // A dropped connection mid-queue is not a failed generation;
                // giving up here would abandon a job that is about to run.
                lastError = e;
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new KavelException(KavelException.Reason.OTHER, "kavel: interrupted", e);
            }
            lastError = null;
            Map<String, Object> polled = data(body);

            List<?> clean = list(polled.get("cleanImages"));
            if (!clean.isEmpty()) {
                return new Image(String.valueOf(clean.get(0)), false);
            }
            List<?> images = list(polled.get("images"));
            if (!images.isEmpty()) {
                List<?> marks = list(polled.get("watermarked"));
                return new Image(String.valueOf(images.get(0)), !marks.isEmpty() && Boolean.TRUE.equals(marks.get(0)));
            }
            Object status = polled.get("status");
            if ("failed".equals(status) || "error".equals(status)) {
                throw new KavelException(KavelException.Reason.REJECTED, "kavel: prompt refused");
            }
        }
    }

    private String send(HttpRequest req) throws KavelException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KavelException(KavelException.Reason.OTHER, "kavel: interrupted", e);
        }
    }

    // Refusals answer HTTP 200 with code -1 and a human message; only the
    // message tells "sign in for this" from an argument mistake.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(String body) throws KavelException {
        Object parsed;
        try {
            parsed = Json.parse(body);
        } catch (RuntimeException e) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: unexpected response: "
                    + body.substring(0, Math.min(body.length(), 200)), e);
        }
        if (!(parsed instanceof Map)) {
            throw new KavelException(KavelException.Reason.OTHER, "kavel: unexpected response");
        }
        Map<String, Object> env = (Map<String, Object>) parsed;
        if (num(env.get("code")) != 0) {
            String msg = String.valueOf(env.get("message"));
            String lower = msg.toLowerCase();
            KavelException.Reason r = KavelException.Reason.OTHER;
            if (lower.contains("invalid api key")) {
                r = KavelException.Reason.AUTH;
            } else if (lower.contains("insufficient credits")) {
                r = KavelException.Reason.QUOTA;
            } else if (lower.contains("sign in") || lower.contains("subscription")) {
                r = KavelException.Reason.SIGN_IN;
            }
            throw new KavelException(r, "kavel: " + msg);
        }
        Object d = env.get("data");
        return d instanceof Map ? (Map<String, Object>) d : Map.of();
    }

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : List.of();
    }

    private static int num(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : -1;
    }

    private static String anonId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder("java-");
        for (byte x : b) {
            s.append(String.format("%02x", x));
        }
        return s.toString();
    }

    /** Configures a {@link Kavel} client. Every setting has a working default. */
    public static final class Builder {
        private String baseUrl = "https://www.kavel.ai";
        private String apiKey;
        private String model;
        private Duration pollEvery = Duration.ofSeconds(5);
        private Duration timeout = Duration.ofMinutes(6);
        private HttpClient http;

        private Builder() {
        }

        /** A key from https://www.kavel.ai/settings/apikeys. Defaults to {@code KAVEL_API_KEY}, then the free tier. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Overrides the engine. Only honoured with an API key; the free tier has one model. */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** How often a job is polled. Defaults to five seconds. */
        public Builder pollEvery(Duration pollEvery) {
            this.pollEvery = pollEvery;
            return this;
        }

        /** Bounds the whole call. Defaults to six minutes, because free runs queue before they start. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Points the client somewhere else, for a test or a proxy. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return this;
        }

        /** Supplies the HTTP client, for a proxy or custom TLS. */
        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        /** @return the configured client */
        public Kavel build() {
            return new Kavel(this);
        }
    }
}

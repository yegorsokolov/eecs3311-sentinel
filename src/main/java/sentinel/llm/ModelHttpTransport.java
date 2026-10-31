package sentinel.llm;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static sentinel.llm.ModelException.Code.*;

/** Shared bounded HTTP transport; one attempt, no redirects, no provider-body disclosure. */
final class ModelHttpTransport {
    private final HttpClient client;
    private final Duration timeout;
    private final int maxResponseBytes;
    ModelHttpTransport(Duration timeout, int maxResponseBytes) {
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build();
    }
    byte[] post(URI endpoint, byte[] payload, String apiKey) {
        if (Thread.currentThread().isInterrupted()) throw failure(INTERRUPTED, "Model request interrupted.");
        var builder = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        if (!apiKey.isEmpty()) builder.header("Authorization", "Bearer " + apiKey);
        var request = builder.build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new BoundedBody(maxResponseBytes));
        HttpResponse<byte[]> response;
        try {
            response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException ex) {
            pending.cancel(true);
            throw failure(TIMEOUT, "Model request timed out.");
        } catch (InterruptedException ex) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(INTERRUPTED, "Model request interrupted.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            while (cause != null) {
                if (cause instanceof ModelException problem) throw problem;
                if (cause instanceof HttpTimeoutException) throw failure(TIMEOUT, "Model request timed out.");
                if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                    throw failure(UNAVAILABLE, "Model provider is unavailable; check the configured service.");
                }
                cause = cause.getCause();
            }
            throw failure(UNAVAILABLE, "Cannot complete the model provider request.");
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) throw failure(AUTHENTICATION, "Model provider rejected authentication.");
        if (status == 404) throw failure(MODEL_NOT_FOUND, "Configured endpoint or model was not found.");
        if (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
            throw failure(TRANSIENT_FAILURE, "Model provider reported a transient failure.");
        }
        if (status != 200) throw failure(HTTP_ERROR, "Model provider rejected the model request.");
        String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].strip();
        if (!"application/json".equalsIgnoreCase(type)) throw failure(INVALID_RESPONSE, "Expected non-streaming JSON.");
        return response.body();
    }

    /** Flow-controlled body accumulation enforces the byte bound before allocating each incoming chunk. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int max) { this.max = max; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > max - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(failure(RESPONSE_TOO_LARGE, "Model response exceeds its byte bound."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }

    private static ModelException failure(ModelException.Code code, String message) { return new ModelException(code, message); }
}

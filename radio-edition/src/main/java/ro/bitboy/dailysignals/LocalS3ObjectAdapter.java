package ro.bitboy.dailysignals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Path-style PUT/GET adapter for an unauthenticated local S3-compatible endpoint. */
public final class LocalS3ObjectAdapter {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;
    private final String bucket;

    public LocalS3ObjectAdapter(URI endpoint, String bucket) {
        if (endpoint == null || !"http".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty() && !"/".equals(endpoint.getPath())))
            throw new IllegalArgumentException("local HTTP endpoint required");
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{2,62}"))
            throw new IllegalArgumentException("invalid bucket");
        this.endpoint = endpoint;
        this.bucket = bucket;
    }
    public URI objectUri(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]*") || key.contains("..") || key.contains("//"))
            throw new IllegalArgumentException("invalid object key");
        return endpoint.resolve("/" + bucket + "/" + key);
    }
    public void put(String key, byte[] data, String contentType) throws IOException, InterruptedException {
        if (data == null || data.length == 0 || !"audio/wav".equals(contentType))
            throw new IllegalArgumentException("nonempty WAV required");
        var request = HttpRequest.newBuilder(objectUri(key)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType).PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) throw new IOException("object PUT failed: " + response.statusCode());
    }
    public byte[] get(String key) throws IOException, InterruptedException {
        var response = client.send(HttpRequest.newBuilder(objectUri(key)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("object GET failed: " + response.statusCode());
        return response.body();
    }
}

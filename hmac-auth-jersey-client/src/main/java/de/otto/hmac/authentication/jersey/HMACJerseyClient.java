package de.otto.hmac.authentication.jersey;

import com.google.common.io.ByteSource;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;
import de.otto.hmac.HmacAttributes;
import de.otto.hmac.StringUtils;
import de.otto.hmac.authentication.RequestSigningUtil;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.joda.time.DateTime;
import org.springframework.util.Assert;

import java.io.IOException;

public class HMACJerseyClient extends ApacheHttpClient4 {

    private String user;
    private String secretKey;
    private String method;
    private String date;
    private String requestUri;
    private ByteSource body = ByteSource.empty();

    private HMACJerseyClient(final ClientConfig cc) {
        super(createDefaultClientHander(cc));
    }

    public HMACJerseyClient auth(final String user, final String secretKey) {
        this.user = user;
        this.secretKey = secretKey;
        return this;
    }

    public WebResource.Builder authenticatedResource(final String url) throws IOException {
        assertAuthentificationPossible();
        date = new DateTime().toString();
        final StringBuilder builder = new StringBuilder(user);
        builder.append(":");
        builder.append(RequestSigningUtil.createRequestSignature(method, date, requestUri, body, secretKey));
        return resource(url).header(HmacAttributes.X_HMAC_AUTH_SIGNATURE, builder.toString()).header(
                HmacAttributes.X_HMAC_AUTH_DATE, date);
    }

    private void assertAuthentificationPossible() throws IOException {
        Assert.isTrue(!StringUtils.isNullOrEmpty(user), "User is desired for authentication");
        Assert.isTrue(!StringUtils.isNullOrEmpty(secretKey), "Secret key is desired for authentication");
        Assert.isTrue(!StringUtils.isNullOrEmpty(method), "Method is desired for authentication");
        Assert.isTrue(!StringUtils.isNullOrEmpty(requestUri), "URI is desired for authentication");
    }

    private boolean isNullOrEmpty(ByteSource byteSource) throws  IOException {
        return byteSource == null || byteSource.isEmpty();
    }

    private static ApacheHttpClient4Handler createDefaultClientHander(final ClientConfig cc) {
        final HttpParams params = new BasicHttpParams();
        final int maxConnections = 20;
        final ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager();
        manager.setDefaultMaxPerRoute(maxConnections);
        manager.setMaxTotal(maxConnections);
        return new ApacheHttpClient4Handler(new DefaultHttpClient(manager, params), null, false);
    }

    public static HMACJerseyClient create() {
        DefaultApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        return new HMACJerseyClient(config);
    }

    public HMACJerseyClient withMethod(final String method) {
        this.method = method;
        return this;
    }

    public HMACJerseyClient withUri(final String uri) {
        this.requestUri = uri;
        return this;
    }

    public HMACJerseyClient withBody(final ByteSource body) {
        this.body = body;
        return this;
    }
}
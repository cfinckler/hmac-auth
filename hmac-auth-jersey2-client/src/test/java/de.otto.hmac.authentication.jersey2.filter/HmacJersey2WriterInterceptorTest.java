package de.otto.hmac.authentication.jersey2.filter;

import com.google.common.io.ByteSource;
import de.otto.hmac.authentication.RequestSigningUtil;
import de.otto.hmac.authentication.WrappedRequest;
import de.otto.hmac.authentication.jersey2.test.JerseyServletTest;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.TestProperties;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for Jersey2 client request filter and writer interceptor combination.
 */
public class HmacJersey2WriterInterceptorTest extends JerseyServletTest {

    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2014, 2, 28, 10, 25).getMillis());
    }

    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void shouldSetHmacHeadersOnGetRequest() {
        final String result = target("returnHmacSignature").request().get(String.class);

        final String requestSignature = result.split(";")[0];
        final String requestHeaderDateTime = result.split(";")[1];
        assertEquals("user:es2fBTAuWtw/eJrkF6PRpFGQjGDodp4HlJvJJ/r4lAk=", requestSignature);
        assertEquals("2014-02-28T09:25:00.000Z", requestHeaderDateTime);

        // verify if signature has been correctly calculated
        final String hmacSignature = requestSignature.split(":")[1];
        final String computedSignature =
                createRequestSignature("GET", requestHeaderDateTime, "/returnHmacSignature", "".getBytes(), "secrectKey");

        assertEquals(hmacSignature, computedSignature);
    }

    @Test
    public void shouldSetHmacHeadersOnPostRequest() throws IOException {
        final Response response = target("returnHmacSignature").request().post(Entity.text("test"));

        assertEquals(200, response.getStatus());
        final InputStream responseContent = response.readEntity(InputStream.class);
        byte[] buffer = new byte[responseContent.available()];
        IOUtils.readFully(responseContent, buffer);
        final String result = new String(buffer, "UTF-8");
        final String requestSignature = result.split(";")[0];
        final String requestHeaderDateTime = result.split(";")[1];
        assertEquals("user:niP5kqthuNz9WhKZUGn6L+BlHk/XVIgRG77OKHO4QV4=", requestSignature);
        assertEquals("2014-02-28T09:25:00.000Z", requestHeaderDateTime);

        // verify if signature has been correctly calculated
        final String hmacSignature = requestSignature.split(":")[1];
        final String computedSignature =
                createRequestSignature("POST", requestHeaderDateTime, "/returnHmacSignature", "test".getBytes(), "secrectKey");

        assertEquals(hmacSignature, computedSignature);
    }

    @Path("returnHmacSignature")
    public static class HmacTestResource {
        @Context
        private HttpServletRequest httpServletRequest;

        @GET
        public String returnHmacSignatureOnGet(@HeaderParam("x-hmac-auth-signature") String hmacAuthSignature,
                @HeaderParam("x-hmac-auth-date") String hmacAuthDate) throws IOException {
            assertTrue(RequestSigningUtil.checkRequest(WrappedRequest.wrap(httpServletRequest), "secrectKey"));
            return hmacAuthSignature + ";" + hmacAuthDate;
        }

        @POST
        public String returnHmacSignatureOnPost(@HeaderParam("x-hmac-auth-signature") String hmacAuthSignature,
                @HeaderParam("x-hmac-auth-date") String hmacAuthDate) throws IOException {
            assertTrue(RequestSigningUtil.checkRequest(WrappedRequest.wrap(httpServletRequest), "secrectKey"));
            return hmacAuthSignature + ";" + hmacAuthDate;
        }
    }

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);
        return new ResourceConfig(HmacTestResource.class);
    }

    @Override
    protected void configureClient(ClientConfig config) {
        config.register(new HmacJersey2ClientRequestFilter("user", "secrectKey"));
    }

    private String createRequestSignature(final String httpMethod, final String dateHeaderString, final String requestUri,
            final byte[] body, final String secretKey) {
        try {
            final SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            final String signatureBase = RequestSigningUtil.createSignatureBase(httpMethod, dateHeaderString, requestUri, ByteSource.wrap(body));
            final byte[] result = mac.doFinal(signatureBase.getBytes());
            return encodeBase64WithoutLinefeed(result);
        } catch (final Exception e) {
            throw new RuntimeException("should never happen", e);
        }
    }

    protected static String encodeBase64WithoutLinefeed(byte[] result) {
        return Base64.encodeBase64String(result).trim();
    }

}

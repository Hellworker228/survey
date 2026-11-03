package org.example.survey.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfInterceptorTest {

    private final CsrfInterceptor interceptor = new CsrfInterceptor();

    @Test
    void allowsGetRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/surveys");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rejectsPostWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/survey/1/delete");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void rejectsPostWithWrongToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/survey/1/delete");
        request.getSession(true);
        request.setParameter(CsrfInterceptor.PARAM, "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void acceptsPostWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/survey/1/delete");
        String token = CsrfInterceptor.tokenFor(request.getSession(true));
        request.setParameter(CsrfInterceptor.PARAM, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void acceptsTokenFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        String token = CsrfInterceptor.tokenFor(request.getSession(true));
        request.addHeader(CsrfInterceptor.HEADER, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}

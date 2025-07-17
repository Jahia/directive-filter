package org.jahia.modules.directivefilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryFilterTest {

    private QueryFilter queryFilter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private StringWriter responseWriter;

    @BeforeEach void setUp() throws IOException {
        queryFilter = new QueryFilter();

        // Setup response writer
        responseWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    private void setupRequestWithBody(String body) throws IOException {
        InputStream is = new ByteArrayInputStream(body.getBytes());
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return is.read();
            }
        });
    }

    @Test void testLessThan10Directives() throws IOException, ServletException {
        // 9 directives
        String input = "@test1 @test2 @test3 @test4 @test5 @test6 @test7 @test8 @test9";
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Filter chain should proceed since request is valid
        verify(filterChain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        // Test with mixed spaces
        String inputMixed = "@test1@test2 @test3@test4 @test5 @test6@test7 @test8 @test9";
        setupRequestWithBody(inputMixed);

        queryFilter.doFilter(request, response, filterChain);
        verify(filterChain, times(2)).doFilter(any(), eq(response));
    }

    @Test void testMoreThan10Directives() throws IOException, ServletException {
        // 11 directives
        String input = "@test1 @test2 @test3 @test4 @test5 @test6 @test7 @test8 @test9 @test10 @test11";
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Should return error response
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(responseWriter.toString().contains("up to 10 consecutive directives"));

        // Test with mixed spaces
        reset(response, filterChain);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        String inputMixed = "@test1@test2@test3 @test4 @test5@test6 @test7@test8 @test9 @test10@test11";
        setupRequestWithBody(inputMixed);

        queryFilter.doFilter(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test void testManyNonNestedBraces() throws IOException, ServletException {
        // 300 non-nested {} pairs
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("{}");
        }
        String input = sb.toString();
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Filter chain should proceed since request is valid
        verify(filterChain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test void testExcessivelyNestedBraces() throws IOException, ServletException {
        // Creates 251 nesting levels
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 251; i++) {
            sb.append("{");
        }
        for (int i = 0; i < 251; i++) {
            sb.append("}");
        }
        String input = sb.toString();
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Should return error response
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(responseWriter.toString().contains("Maximum allowed depth exceeded"));
    }

    @Test void testMaxAllowedDepth() throws IOException, ServletException {
        // Exactly 250 nesting levels
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            sb.append("{");
        }
        for (int i = 0; i < 250; i++) {
            sb.append("}");
        }
        String input = sb.toString();
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Filter chain should proceed since request is valid
        verify(filterChain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test void testMoreOpenBracesThanCloseBraces() throws IOException, ServletException {
        String input = "{{{}";  // 3 opening braces, 1 closing brace
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Should return error response
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(responseWriter.toString().contains("Unmatched braces"));
    }

    @Test void testMoreCloseBracesThanOpenBraces() throws IOException, ServletException {
        String input = "{}}";  // 1 opening brace, 2 closing braces
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Should return error response
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(any(), any());
        assertTrue(responseWriter.toString().contains("Unmatched braces"));
    }

    @Test void testEmptyInput() throws IOException, ServletException {
        String input = "";
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Filter chain should proceed since request is valid
        verify(filterChain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test void testComplexQueryWithMixedDirectivesAndBraces() throws IOException, ServletException {
        String input = "query { @directive1 @directive2 field { nested { @directive3 value } } @directive4 }";
        setupRequestWithBody(input);

        queryFilter.doFilter(request, response, filterChain);

        // Filter chain should proceed since request is valid
        verify(filterChain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
}

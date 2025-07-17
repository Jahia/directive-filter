/*
 * Copyright (C) 2002-2022 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jahia.modules.directivefilter;

import org.osgi.service.component.annotations.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

@Component(service = {Filter.class}, property = {"pattern=/graphql"}, immediate = true)
public class QueryFilter implements Filter {

    // Detect strings such as @lala@lala@lala... and with space(s) in between @lala @lala
    private static final Pattern DIRECTIVE_REGEX = Pattern.compile("(@[^ @]+[\\s]*){10}");

    @Override
    public void init(FilterConfig filterConfig)  {
        // Initialization logic if needed
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletResponse instanceof HttpServletResponse && servletRequest instanceof HttpServletRequest) {
            MultiReadRequestWrapper r = new MultiReadRequestWrapper((HttpServletRequest) servletRequest);
            String query = r.getReader().lines().collect(joining(" "));

            if (DIRECTIVE_REGEX.matcher(query).find()) {
                HttpServletResponse resp = (HttpServletResponse) servletResponse;
                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"message\": \"You can only use up to 10 consecutive directives\"}");
                resp.getWriter().flush();
                return;
            }

            try {
                checkMaxDepth(query);
            } catch (InvalidParameterException e) {
                HttpServletResponse resp = (HttpServletResponse) servletResponse;
                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"message\": \"" + e.getMessage() + "\"}");
                resp.getWriter().flush();
                return;
            }
            filterChain.doFilter(r, servletResponse);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    private void checkMaxDepth(String input) throws InvalidParameterException {
        final int maxAllowedDepth = 250;
        int maxDepth = 0;
        int currentDepth = 0;
        char[] chars = input.toCharArray();

        for (char c : chars) {
            if (c == '{') {
                currentDepth++;
                if (currentDepth > maxDepth) {
                    maxDepth = currentDepth;
                }
            } else if (c == '}') {
                currentDepth--;
                if (currentDepth < 0) {
                    throw new InvalidParameterException("Unmatched braces in input string");
                }
            }
            if (currentDepth > maxAllowedDepth) {
                throw new InvalidParameterException("Maximum allowed depth exceeded: " + maxAllowedDepth);
            }
        }

        if (currentDepth != 0) {
            throw new InvalidParameterException("Unmatched braces in input string");
        }
    }

}

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

@Component(service = {Filter.class}, property = {"pattern=/graphql"}, immediate = true)
public class QueryFilter implements Filter {

    // Detect strings such as @lala@lala@lala... and with space(s) in between @lala @lala
    private Pattern regex = Pattern.compile("(@[^ @]+[ ]*){10}");
    private Pattern deep = Pattern.compile("\\{");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletResponse instanceof HttpServletResponse && servletRequest instanceof HttpServletRequest) {
            MultiReadRequestWrapper r = new MultiReadRequestWrapper((HttpServletRequest) servletRequest);
            String query = r.getReader().lines().collect(joining(" "));

            if (regex.matcher(query).find()) {
                HttpServletResponse resp = (HttpServletResponse) servletResponse;
                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"message\": \"You can only use up to 10 consecutive directives\"}");
                resp.getWriter().flush();
                return;
            }

            Matcher matcher = deep.matcher(query);
            int deepLevel = 0;
            while (matcher.find()) {
                deepLevel++;
                if(deepLevel > 250) {
                    break;
                }
            }

            if(deepLevel > 250) {
                HttpServletResponse resp = (HttpServletResponse) servletResponse;
                resp.setContentType("application/json");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("{\"message\": \"You can only use up to 250 depths\"}");
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

    }
}

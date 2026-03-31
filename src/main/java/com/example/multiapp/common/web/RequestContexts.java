package com.example.multiapp.common.web;

import com.example.multiapp.common.tenant.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

public class RequestContexts {
    @Getter
    private final static String ATTR = "ctx";
    private RequestContexts() {}

    public static RequestContext require(HttpServletRequest req) {
        Object v = req.getAttribute(ATTR);
        if (v instanceof RequestContext c) return c;
        throw new IllegalStateException("RequestContext missing");
    }
}

package com.example.multiapp.contact.auth;

import com.example.multiapp.common.tenant.RequestContext;

public interface ContactAuthorizer {
    void requireCreate(RequestContext ctx);
    void requireList(RequestContext ctx);
    void requireRead(RequestContext ctx);
    void requireUpdate(RequestContext ctx);
}

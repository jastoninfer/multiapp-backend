package com.example.multiapp.comment.model;

import com.example.multiapp.common.event.DomainEventType;

public enum CommentEventType implements DomainEventType {
    COMMENT_CREATED;

    @Override
    public String key() {
        return name();
    }
}

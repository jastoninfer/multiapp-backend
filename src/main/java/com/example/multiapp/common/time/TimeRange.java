package com.example.multiapp.common.time;

import java.time.OffsetDateTime;
import java.util.Objects;

public record TimeRange(
        OffsetDateTime start,
        OffsetDateTime end
) {
    public TimeRange {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if(!end.isAfter(start)) throw new IllegalArgumentException("end must be > start");
    }

    public boolean overlaps(TimeRange other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean covers(TimeRange inner) {
        return !this.start.isAfter(inner.start) && !this.end.isBefore(inner.end);
    }
}

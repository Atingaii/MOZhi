package cn.zy.mozhi.types.event;

import java.time.Instant;

public record BaseEvent(String eventId, Instant occurredAt) {
}

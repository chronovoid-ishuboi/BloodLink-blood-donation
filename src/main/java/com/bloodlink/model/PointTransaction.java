package com.bloodlink.model;

import java.time.LocalDateTime;

public record PointTransaction(long id, long donorId, int delta, String reason,
                                Long relatedRequestId, String note, LocalDateTime createdAt) { }

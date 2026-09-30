package com.fasthire.source;

public record Classification(SourceKind kind, String atsType, String atsToken) {
    static Classification ats(String type, String token) {
        return new Classification(SourceKind.ATS, type, token);
    }
    static final Classification BOARD = new Classification(SourceKind.BOARD_EMAIL_ONLY, null, null);
    static final Classification UNSUPPORTED = new Classification(SourceKind.UNSUPPORTED, null, null);
}

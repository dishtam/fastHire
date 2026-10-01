package com.fasthire.notify;

import java.io.IOException;

public interface Notifier {
    void send(String text) throws IOException;
}

package util;

/**
 * All rights reserved.
 * (c) 1991-2024 Amacker&Partner Informatik AG
 * Albisriederstr. 252A, CH-8047 Zürich, Switzerland
 */

public class MessageUtil {

    public static void printSynchronizedMessage(String message) {
        synchronized (System.out) {
            System.out.println(message);
            System.out.flush();
        }
    }

}

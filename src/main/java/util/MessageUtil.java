package util;

public class MessageUtil {

    public static void printSynchronizedMessage(String message) {
        synchronized (System.out) {
            System.out.println(message);
            System.out.flush();
        }
    }
}

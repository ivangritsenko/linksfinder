package linksfinder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.MessageUtil.printSynchronizedMessage;

public class LinksFinder {

    // Preceding with any of: single quote, double quote, space, new line
    private static final String REGEX_STARTS_WITH = "(?<=['\" \\n\\r])";
    // Ends with any of: question mark (get request params), single quote, double quote, space, new line
    private static final String REGEX_ENDS_WITH = "[^?'\" \\n\\r]+";
    private static final long NANOSECONDS_IN_SECOND = 1_000_000_000;

    private final String urlToBeginWith;
    private final Pattern urlLinkRegexPattern;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ConcurrentHashMap<String, String> links = new ConcurrentHashMap<>();
    private final long nanoOnStartOfTheProcess;
    private final AtomicInteger numberOfPlannedTasks = new AtomicInteger(0);

    public static void findAndPrintAllLinksFrom(String URL) {
        LinksFinder linksFinder = new LinksFinder(URL);
        linksFinder.lookupAndPrintLinks();
    }

    private LinksFinder(String urlToBeginWith) {
        this.urlToBeginWith = urlToBeginWith;
        this.urlLinkRegexPattern = Pattern.compile(REGEX_STARTS_WITH + this.urlToBeginWith + REGEX_ENDS_WITH);
        this.nanoOnStartOfTheProcess = System.nanoTime();
    }

    private void lookupAndPrintLinks() {
        submitTaskToServiceExecutor(() -> processPageWithFinalizer(this.urlToBeginWith));

        while (executorHasUnfinishedTasks()) {
            try {
                Thread.sleep(5000);
                System.out.println("Current number of planned tasks " + numberOfPlannedTasks);
            } catch (InterruptedException e) {
                System.out.println("Main thread was interrupted.");
            }
        }

        printLinksToConsole();
        printFinalStatistics();

        executorService.shutdown();
    }

    private boolean executorHasUnfinishedTasks() {
        return numberOfPlannedTasks.get() > 0;
    }

    private void processPage(String pageUrl) {
        URL url;
        try {
            url = URI.create(pageUrl).toURL();
        } catch (MalformedURLException e) {
            printSynchronizedMessage("MalformedURLException " + pageUrl);
            return;
        } catch (Throwable e) {
            printSynchronizedMessage("Some error happened for URL " + pageUrl);
            return;
        }

        StringBuilder htmlPageContentStringBuilder = readHtmlPageContent(url);
        String wholeHtml = htmlPageContentStringBuilder.toString();
        String[] htmlLines = wholeHtml.split("\n");
        collectLinksFrom(htmlLines);
    }

    private void processPageWithFinalizer(String urlToBeginWith) {
        try {
            processPage(urlToBeginWith);
        } finally {
            numberOfPlannedTasks.decrementAndGet();
        }
    }

    private StringBuilder readHtmlPageContent(URL url) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.addRequestProperty("User-Agent", "Mozilla/4.0");
            InputStream inputStream = httpURLConnection.getInputStream();

            int inputStreamByte = readByteFrom(inputStream);
            stringBuilder = new StringBuilder();

            while (endOfInputStreamIsNotReached(inputStreamByte)) {
                stringBuilder.append((char) inputStreamByte);
                inputStreamByte = readByteFrom(inputStream);
            }
        } catch (IOException e) {
            printSynchronizedMessage("Couldn't read URL " + url + ", exception " + e);
        }

        return stringBuilder;
    }

    private int readByteFrom(InputStream inputStream) throws IOException {
        return inputStream.read();
    }

    private boolean endOfInputStreamIsNotReached(int byteReadFromInputStream) {
        return byteReadFromInputStream != -1;
    }

    private void collectLinksFrom(String[] htmlLines) {
        for (String htmlLine : htmlLines) {
            Matcher matcher = urlLinkRegexPattern.matcher(htmlLine);

            while (matcher.find()) {
                String matchedLink = processMatchedLink(matcher.group(0));

                synchronized(this) {
                    if (!links.containsKey(matchedLink)) {
                        links.put(matchedLink, "");
                        submitTaskToServiceExecutor(() -> processPageWithFinalizer(matchedLink));
                    }
                }
            }
        }
    }

    private String processMatchedLink(String matchedLink) {
        String finalLink = matchedLink;

        if (lastChartIsSlash(matchedLink)) {
            finalLink = truncateLastChart(finalLink);
        }

        return finalLink;
    }

    private String truncateLastChart(String str) {
        return str.substring(0, str.length() - 1);
    }

    private boolean lastChartIsSlash(String matchedLink) {
        return matchedLink.charAt(matchedLink.length() - 1) == '/';
    }

    private void printLinksToConsole() {
        links.keySet()
                .stream()
                .sorted()
                .forEach(System.out::println);
    }

    private void printFinalStatistics() {
        long secondsSpent = (System.nanoTime() - nanoOnStartOfTheProcess) / NANOSECONDS_IN_SECOND;
        printSynchronizedMessage(String.format("Found %s links in %s seconds.", links.size(), secondsSpent));
    }

    private void submitTaskToServiceExecutor(Runnable runnable) {
        numberOfPlannedTasks.incrementAndGet();
        executorService.execute(runnable);
    }
}

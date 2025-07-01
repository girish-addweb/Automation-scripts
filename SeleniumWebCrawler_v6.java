import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public class SeleniumWebCrawler_v6 {

    private WebDriver driver;
    private WebDriverWait wait;
    private Set<String> visitedUrls;
    private Set<String> queuedUrls; // Track URLs already added to queue
    private Queue<String> urlsToVisit;
    private List<CrawlResult> results;
    private String baseDomain;
    private int maxPages;
    private long delayMs;
    private PrintWriter csvWriter;
    private int serialNumber;
    private ExecutorService statusCheckExecutor;

    // Configuration flags
    private boolean checkConsoleErrors;
    private boolean useDetailedErrorTracking;
    private boolean checkBrokenImages;
    private boolean useLambdaTest;

    // LambdaTest Configuration (configurable constants)
    private static final boolean LAMBDATEST_ENABLED = false; // Set to true to enable LambdaTest
    private static final String LAMBDATEST_USERNAME = "akash.p";
    private static final String LAMBDATEST_ACCESS_KEY = "mTI4uE1fDkbhtZq1zItC9euilorAdqOIJgl3EHV3MWFMzEPQSa";
    private static final String LAMBDATEST_HUB_URL = "https://hub.lambdatest.com/wd/hub";

    // Default Configuration - Optimized for better performance
    private static final int DEFAULT_MAX_PAGES = 5000;
    private static final long DEFAULT_DELAY_MS = 500; // Reduced from 1000ms for faster crawling
    private static final int REQUEST_TIMEOUT = 3000; // Reduced from 5000ms
    private static final int THREAD_POOL_SIZE = 15; // Increased for better parallel processing

    public SeleniumWebCrawler_v6(String startUrl, int maxPages, long delayMs, boolean headless,
                                 boolean checkConsoleErrors, boolean useDetailedErrorTracking,
                                 boolean checkBrokenImages, boolean useLambdaTest) {
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.queuedUrls = ConcurrentHashMap.newKeySet();
        this.urlsToVisit = new ConcurrentLinkedQueue<>(); // Better for concurrent access
        this.results = Collections.synchronizedList(new ArrayList<>());
        this.maxPages = maxPages;
        this.delayMs = delayMs;
        this.serialNumber = 1;
        this.checkConsoleErrors = checkConsoleErrors;
        this.useDetailedErrorTracking = useDetailedErrorTracking;
        this.checkBrokenImages = checkBrokenImages;
        this.useLambdaTest = useLambdaTest;
        // Initialize thread pool for parallel status checks
        this.statusCheckExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            URL url = new URL(startUrl);
            this.baseDomain = url.getHost();
            String normalizedStartUrl = normalizeUrl(startUrl);
            this.urlsToVisit.offer(normalizedStartUrl);
            this.queuedUrls.add(normalizedStartUrl);
        } catch (MalformedURLException e) {
            System.err.println("Invalid start URL: " + e.getMessage());
        }

        setupWebDriver(headless);
        setupCSVWriter();
    }
    private void setupWebDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();

        if (useLambdaTest) {
            // LambdaTest Configuration
            options.setPlatformName("Windows 10");
            options.setBrowserVersion("137");
            HashMap<String, Object> ltOptions = new HashMap<String, Object>();
            ltOptions.put("username", LAMBDATEST_USERNAME);
            ltOptions.put("accessKey", LAMBDATEST_ACCESS_KEY);
            ltOptions.put("project", "Full Site Tester v6");
            ltOptions.put("w3c", true);
            ltOptions.put("headless", headless);
            options.setCapability("LT:Options", ltOptions);
            try {
                this.driver = new RemoteWebDriver(new URL(LAMBDATEST_HUB_URL), options);
                System.out.println("Running on LambdaTest cloud");
            } catch (MalformedURLException e) {
                System.err.println("Error setting up LambdaTest: " + e.getMessage());
                System.out.println("Falling back to local execution");
                setupLocalDriver(headless);
            }
        } else {
            setupLocalDriver(headless);
        }

        this.wait = new WebDriverWait(driver, Duration.ofSeconds(3)); // Further reduced timeout
    }
    private void setupLocalDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();

        // Optimized Chrome options for maximum performance
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images"); // Skip image loading for faster performance
        options.addArguments("--disable-javascript"); // Disable JS for external links (we only check status)
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--aggressive-cache-discard");

        if (headless) {
            options.addArguments("--headless");
            System.out.println("Running in headless mode locally (v6)");
        } else {
            System.out.println("Running locally with GUI (v6)");
        }

        this.driver = new ChromeDriver(options);
    }
    private void setupCSVWriter() {
        try {
            FileWriter fileWriter = new FileWriter("crawl_results_v6.csv");
            this.csvWriter = new PrintWriter(fileWriter);

            // Write CSV header - simplified for external links
            String header = "Sr No,URL,Status Code,Type";
            if (checkConsoleErrors) header += ",Console Errors";
            if (checkBrokenImages) header += ",Broken Images";

            csvWriter.println(header);
            csvWriter.flush();
        } catch (IOException e) {
            System.err.println("Error setting up CSV writer: " + e.getMessage());
        }
    }
    public void crawl() {
        System.out.println("Starting crawl from: " + urlsToVisit.peek());
        System.out.println("Configuration: Max pages=" + maxPages + ", Delay=" + delayMs + "ms");
        System.out.println("Features: Console Errors=" + checkConsoleErrors +
                ", Broken Images=" + checkBrokenImages +
                ", LambdaTest=" + useLambdaTest);
        int crawledCount = 0;
        List<Future<CrawlResult>> statusCheckFutures = new ArrayList<>();

        while (!urlsToVisit.isEmpty() && crawledCount < maxPages) {
            String currentUrl = urlsToVisit.poll();

            if (visitedUrls.contains(currentUrl)) {
                continue;
            }
            // Skip fragment/hash links and common file extensions
            if (shouldSkipUrl(currentUrl)) {
                System.out.println("Skipping URL: " + currentUrl);
                visitedUrls.add(currentUrl);
                continue;
            }
            visitedUrls.add(currentUrl);
            boolean isInternal = isInternalLink(currentUrl);

            if (isInternal) {
                // Crawl internal links with full analysis
                CrawlResult result = crawlInternalPage(currentUrl);
                if (result != null) {
                    results.add(result);
                    logResult(result);
                    crawledCount++;

                    // Extract links from internal pages only
                    extractLinks(currentUrl);
                }
            } else {
                // For external links, only check status code asynchronously
                // NO console errors or broken images checking
                Future<CrawlResult> future = statusCheckExecutor.submit(() -> {
                    CrawlResult result = new CrawlResult();
                    synchronized (this) {
                        result.serialNumber = serialNumber++;
                    }
                    result.url = currentUrl;
                    result.statusCode = getHttpStatusCode(currentUrl);
                    result.linkType = "External";
                    // External links don't get error checking
                    result.consoleErrors = "N/A";
                    result.brokenImages = "N/A";
                    return result;
                });
                statusCheckFutures.add(future);
                crawledCount++;

                // IMPORTANT: Do NOT extract links from external pages
            }
            // Process completed external link checks
            processCompletedStatusChecks(statusCheckFutures);
            // Progress update with better information
            if (crawledCount % 25 == 0) {
                int internalCount = (int) results.stream().mapToLong(r -> "Internal".equals(r.linkType) ? 1 : 0).sum();
                int externalCount = (int) results.stream().mapToLong(r -> "External".equals(r.linkType) ? 1 : 0).sum();
                System.out.println(String.format("Progress: %d URLs processed (%d internal, %d external), %d in queue, %d total discovered",
                        crawledCount, internalCount, externalCount, urlsToVisit.size(), visitedUrls.size()));
            }
            // Rate limiting
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Wait for remaining status checks to complete
        waitForRemainingStatusChecks(statusCheckFutures);

        System.out.println("Crawling completed. Total URLs processed: " + crawledCount);
        System.out.println("Total unique URLs discovered: " + visitedUrls.size());
        cleanup();
    }
    private boolean shouldSkipUrl(String url) {
        // Skip fragment/hash links
        if (url.contains("#")) {
            return true;
        }
        // Skip common file extensions that don't need crawling
        String lowerUrl = url.toLowerCase();
        String[] skipExtensions = {".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".ico",
                ".mp3", ".mp4", ".avi", ".mov", ".wav", ".zip", ".rar", ".tar.gz"};
        for (String ext : skipExtensions) {
            if (lowerUrl.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }
    private void processCompletedStatusChecks(List<Future<CrawlResult>> futures) {
        Iterator<Future<CrawlResult>> iterator = futures.iterator();
        while (iterator.hasNext()) {
            Future<CrawlResult> future = iterator.next();
            if (future.isDone()) {
                try {
                    CrawlResult result = future.get();
                    results.add(result);
                    logResult(result);
                    iterator.remove();
                } catch (Exception e) {
                    System.err.println("Error processing external link status check: " + e.getMessage());
                    iterator.remove();
                }
            }
        }
    }
    private void waitForRemainingStatusChecks(List<Future<CrawlResult>> futures) {
        System.out.println("Waiting for remaining external link status checks...");
        for (Future<CrawlResult> future : futures) {
            try {
                CrawlResult result = future.get(5, TimeUnit.SECONDS); // Reduced timeout
                results.add(result);
                logResult(result);
            } catch (Exception e) {
                System.err.println("Timeout or error waiting for status check: " + e.getMessage());
            }
        }
    }

    private CrawlResult crawlInternalPage(String url) {
        CrawlResult result = new CrawlResult();
        synchronized (this) {
            result.serialNumber = serialNumber++;
        }
        result.url = url;
        try {
            // Navigate to URL
            driver.get(url);
            // Smart wait for page load with shorter timeout
            try {
                wait.until(ExpectedConditions.jsReturnsValue("return document.readyState === 'complete'"));
            } catch (TimeoutException e) {
                // Continue anyway, page might be functional
            }
            // Get status code
            result.statusCode = getHttpStatusCode(url);
            result.linkType = "Internal";
            // Check console errors if enabled (ONLY for internal links)
            if (checkConsoleErrors) {
                result.consoleErrors = getConsoleErrors();
            } else {
                result.consoleErrors = "Not checked";
            }
            // Check broken images if enabled (ONLY for internal links)
            if (checkBrokenImages) {
                result.brokenImages = getBrokenImages();
            } else {
                result.brokenImages = "Not checked";
            }
            return result;
        } catch (Exception e) {
            System.err.println("Error crawling " + url + ": " + e.getMessage());
            result.statusCode = 0;
            result.linkType = "Internal";
            result.consoleErrors = "Navigation Error";
            result.brokenImages = "Could not check";
            return result;
        }
    }
    private int getHttpStatusCode(String url) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(REQUEST_TIMEOUT);
            connection.setReadTimeout(REQUEST_TIMEOUT);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setInstanceFollowRedirects(true); // Follow redirects
            connection.connect();
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode;

        } catch (Exception e) {
            // Only log errors for debugging, not for every failed external link
            return 0;
        }
    }
    private boolean isInternalLink(String url) {
        try {
            URL urlObj = new URL(url);
            String host = urlObj.getHost().toLowerCase();
            String baseDomainLower = baseDomain.toLowerCase();

            return host.equals(baseDomainLower) ||
                    host.endsWith("." + baseDomainLower) ||
                    baseDomainLower.endsWith("." + host); // Handle www vs non-www
        } catch (MalformedURLException e) {
            return false;
        }
    }
    private String getConsoleErrors() {
        if (!checkConsoleErrors) return "Not checked";

        StringBuilder errors = new StringBuilder();

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Optimized error checking script
            String errorCheckScript =
                    "var errors = [];" +
                            "try {" +
                            // Check for JavaScript errors on the page
                            "  if (window.errors && window.errors.length > 0) {" +
                            "    errors = errors.concat(window.errors);" +
                            "  }" +
                            // Check for 404 errors on resources
                            "  var failedResources = [];" +
                            "  var allImages = document.getElementsByTagName('img');" +
                            "  for (var i = 0; i < Math.min(allImages.length, 50); i++) {" + // Limit to first 50 images
                            "    var img = allImages[i];" +
                            "    if (img.complete && img.naturalWidth === 0 && img.src) {" +
                            "      failedResources.push('Failed resource: ' + img.src);" +
                            "    }" +
                            "  }" +
                            "  errors = errors.concat(failedResources);" +
                            "} catch(e) {" +
                            "  errors.push('Error checking: ' + e.message);" +
                            "}" +
                            "return errors.slice(0, 10).join(' | ');"; // Limit to first 10 errors
            String result = (String) js.executeScript(errorCheckScript);
            if (result != null && !result.trim().isEmpty()) {
                errors.append(result);
            }
        } catch (Exception e) {
            errors.append("Error checking console: ").append(e.getMessage());
        }
        return errors.length() > 0 ? errors.toString() : "None";
    }
    private String getBrokenImages() {
        if (!checkBrokenImages) return "Not checked";

        StringBuilder brokenImages = new StringBuilder();

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Optimized broken image detection
            String script =
                    "var broken = [];" +
                            "var images = document.getElementsByTagName('img');" +
                            "var limit = Math.min(images.length, 100);" + // Limit check to first 100 images
                            "for (var i = 0; i < limit; i++) {" +
                            "    var img = images[i];" +
                            "    if (img.complete && img.naturalWidth === 0 && img.src) {" +
                            "        broken.push(img.src);" +
                            "    }" +
                            "}" +
                            "return broken.slice(0, 20).join(';');"; // Limit to first 20 broken images

            String result = (String) js.executeScript(script);
            if (result != null && !result.trim().isEmpty()) {
                brokenImages.append(result);
            }

        } catch (Exception e) {
            brokenImages.append("Error checking images: ").append(e.getMessage());
        }
        return brokenImages.length() > 0 ? brokenImages.toString() : "None";
    }
    private void extractLinks(String currentUrl) {
        try {
            List<WebElement> links = driver.findElements(By.tagName("a"));
            Set<String> newUrls = new HashSet<>();

            for (WebElement link : links) {
                try {
                    String href = link.getAttribute("href");
                    if (href != null && !href.trim().isEmpty() &&
                            (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("/"))) {

                        // Convert relative URLs to absolute
                        if (href.startsWith("/")) {
                            try {
                                URL baseUrl = new URL(currentUrl);
                                href = baseUrl.getProtocol() + "://" + baseUrl.getHost() +
                                        (baseUrl.getPort() != -1 ? ":" + baseUrl.getPort() : "") + href;
                            } catch (MalformedURLException e) {
                                continue;
                            }
                        }
                        // Skip URLs that should be skipped
                        if (shouldSkipUrl(href)) {
                            continue;
                        }
                        // Normalize URL
                        href = normalizeUrl(href);
                        // Add to newUrls set (automatically handles duplicates within this page)
                        newUrls.add(href);
                    }
                } catch (Exception e) {// Skip this link and continue
                    continue;
                }
            }
            // Process all new URLs from this page
            for (String href : newUrls) {
                // Check if URL has not been visited AND not been queued
                if (!visitedUrls.contains(href) && queuedUrls.add(href)) {
                    // queuedUrls.add() returns true if href was not already in the set
                    urlsToVisit.offer(href);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting links from " + currentUrl + ": " + e.getMessage());
        }
    }
    // Enhanced URL normalization
    private String normalizeUrl(String url) {
        try {
            // Remove fragment/hash
            if (url.contains("#")) {
                url = url.substring(0, url.indexOf("#"));
            }

            // Remove trailing slash for consistency, except for root URLs
            if (url.endsWith("/") && !url.matches("https?://[^/]+/?$")) {
                url = url.substring(0, url.length() - 1);
            }

            // Convert to lowercase for consistent comparison
            URL urlObj = new URL(url);
            return urlObj.getProtocol().toLowerCase() + "://" +
                    urlObj.getHost().toLowerCase() +
                    (urlObj.getPort() != -1 ? ":" + urlObj.getPort() : "") +
                    urlObj.getPath() +
                    (urlObj.getQuery() != null ? "?" + urlObj.getQuery() : "");

        } catch (Exception e) {
            return url;
        }
    }
    private void logResult(CrawlResult result) {
        // Enhanced console output - NO error/broken image info for external links
        StringBuilder output = new StringBuilder();
        output.append(String.format("Sr: %d | URL: %s | Status: %d | Type: %s",
                result.serialNumber, result.url, result.statusCode, result.linkType));
        // Only show error/broken image info for INTERNAL links and when checking is enabled
        if ("Internal".equals(result.linkType)) {
            if (checkConsoleErrors && !"Not checked".equals(result.consoleErrors) && !"None".equals(result.consoleErrors)) {
                output.append(" | Errors: Yes");
            }
            if (checkBrokenImages && !"Not checked".equals(result.brokenImages) && !"None".equals(result.brokenImages)) {
                int brokenCount = result.brokenImages.split(";").length;
                output.append(" | Broken Images: ").append(brokenCount);
            }
        }
        // For external links, NO additional error information is shown
        System.out.println(output.toString());
        // CSV output
        StringBuilder csvLine = new StringBuilder();
        csvLine.append(String.format("%d,\"%s\",%d,%s",
                result.serialNumber,
                result.url.replace("\"", "\"\""),
                result.statusCode,
                result.linkType));

        if (checkConsoleErrors) {
            csvLine.append(",\"").append(result.consoleErrors.replace("\"", "\"\"")).append("\"");
        }
        if (checkBrokenImages) {
            csvLine.append(",\"").append(result.brokenImages.replace("\"", "\"\"")).append("\"");
        }
        csvWriter.println(csvLine.toString());
        csvWriter.flush();
    }
    private void cleanup() {
        if (statusCheckExecutor != null) {
            statusCheckExecutor.shutdown();
            try {
                if (!statusCheckExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    statusCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                statusCheckExecutor.shutdownNow();
            }
        }
        if (driver != null) {
            driver.quit();
        }
        if (csvWriter != null) {
            csvWriter.close();
        }
        generateReport();
    }
    private void generateReport() {
        try {
            PrintWriter reportWriter = new PrintWriter(new FileWriter("crawl_report_v6.txt"));
            reportWriter.println("CRAWL SUMMARY REPORT (v6)");
            reportWriter.println("=========================");
            reportWriter.println("Total URLs Processed: " + results.size());
            reportWriter.println("Total URLs Discovered: " + visitedUrls.size());
            reportWriter.println("Total URLs Queued: " + queuedUrls.size());
            reportWriter.println("Base Domain: " + baseDomain);
            reportWriter.println("Configuration:");
            reportWriter.println("  - Console Errors: " + checkConsoleErrors);
            reportWriter.println("  - Detailed Errors: " + useDetailedErrorTracking);
            reportWriter.println("  - Broken Images: " + checkBrokenImages);
            reportWriter.println("  - LambdaTest: " + useLambdaTest);
            reportWriter.println();
            // Status code summary
            Map<Integer, Integer> statusCounts = new HashMap<>();
            int internalCount = 0, externalCount = 0;
            int internalPagesWithErrors = 0, internalPagesWithBrokenImages = 0;

            for (CrawlResult result : results) {
                statusCounts.put(result.statusCode,
                        statusCounts.getOrDefault(result.statusCode, 0) + 1);

                if ("Internal".equals(result.linkType)) {
                    internalCount++;
                    // Only count errors for internal links
                    if (checkConsoleErrors && !"Not checked".equals(result.consoleErrors) &&
                            !"None".equals(result.consoleErrors) && !result.consoleErrors.trim().isEmpty()) {
                        internalPagesWithErrors++;
                    }
                    if (checkBrokenImages && !"Not checked".equals(result.brokenImages) &&
                            !"None".equals(result.brokenImages) && !result.brokenImages.trim().isEmpty()) {
                        internalPagesWithBrokenImages++;
                    }
                } else if ("External".equals(result.linkType)) {
                    externalCount++;
                }
            }
            reportWriter.println("STATUS CODE DISTRIBUTION:");
            statusCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> reportWriter.println("  " + entry.getKey() + ": " + entry.getValue() + " pages"));
            reportWriter.println();
            reportWriter.println("LINK TYPE DISTRIBUTION:");
            reportWriter.println("  Internal Links: " + internalCount);
            reportWriter.println("  External Links: " + externalCount);

            if (checkConsoleErrors || checkBrokenImages) {
                reportWriter.println();
                reportWriter.println("ERROR SUMMARY (Internal Links Only):");
                if (checkConsoleErrors) {
                    reportWriter.println("  Internal Pages with Console Errors: " + internalPagesWithErrors);
                }
                if (checkBrokenImages) {
                    reportWriter.println("  Internal Pages with Broken Images: " + internalPagesWithBrokenImages);
                }
            }
            reportWriter.println();
            reportWriter.println("PERFORMANCE METRICS:");
            reportWriter.println("  Thread Pool Size: " + THREAD_POOL_SIZE);
            reportWriter.println("  Request Timeout: " + REQUEST_TIMEOUT + "ms");
            reportWriter.println("  Delay Between Requests: " + delayMs + "ms");
            reportWriter.close();
            System.out.println("Reports generated: crawl_results_v6.csv, crawl_report_v6.txt");
        } catch (IOException e) {
            System.err.println("Error generating report: " + e.getMessage());
        }
    }
    // Inner class to hold crawl results
    private static class CrawlResult {
        int serialNumber;
        String url;
        int statusCode;
        String linkType;
        String consoleErrors;
        String brokenImages;
    }
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter starting URL: ");
        String startUrl = scanner.nextLine().trim();

        if (startUrl.isEmpty()) {
            startUrl = "https://www.shreeradheydairy.com/"; // Default for testing
        }
        System.out.print("Enter max pages to crawl (default " + DEFAULT_MAX_PAGES + "): ");
        String maxPagesInput = scanner.nextLine().trim();
        int maxPages = maxPagesInput.isEmpty() ? DEFAULT_MAX_PAGES : Integer.parseInt(maxPagesInput);

        System.out.print("Enter delay between requests in ms (default " + DEFAULT_DELAY_MS + "): ");
        String delayInput = scanner.nextLine().trim();
        long delay = delayInput.isEmpty() ? DEFAULT_DELAY_MS : Long.parseLong(delayInput);

        System.out.print("Run in headless mode? (y/n, default y): ");
        String headlessInput = scanner.nextLine().trim();
        boolean headless = headlessInput.isEmpty() || headlessInput.toLowerCase().equals("y") || headlessInput.toLowerCase().equals("yes");
        // LambdaTest configuration
        boolean useLambdaTest = false;
        if (LAMBDATEST_ENABLED) {
            System.out.print("Use LambdaTest cloud? (y/n, default n): ");
            String lambdaTestInput = scanner.nextLine().trim();
            useLambdaTest = lambdaTestInput.toLowerCase().equals("y") || lambdaTestInput.toLowerCase().equals("yes");
        }
        // Console error checking configuration
        System.out.print("Check console errors for internal links? (y/n, default y): ");
        String consoleErrorInput = scanner.nextLine().trim();
        boolean checkConsoleErrors = consoleErrorInput.isEmpty() ||
                consoleErrorInput.toLowerCase().equals("y") ||
                consoleErrorInput.toLowerCase().equals("yes");
        boolean useDetailedErrorTracking = false;
        if (checkConsoleErrors) {
            System.out.print("Use detailed error tracking? (slower but comprehensive) (y/n, default n): ");
            String detailedInput = scanner.nextLine().trim();
            useDetailedErrorTracking = detailedInput.toLowerCase().equals("y") ||
                    detailedInput.toLowerCase().equals("yes");
            System.out.print("Check broken images? (y/n, default y): ");
            String brokenImageInput = scanner.nextLine().trim();
            boolean checkBrokenImages = brokenImageInput.isEmpty() ||
                    brokenImageInput.toLowerCase().equals("y") ||
                    brokenImageInput.toLowerCase().equals("yes");
            scanner.close();
            System.out.println("\n=== CRAWL CONFIGURATION ===");
            System.out.println("Start URL: " + startUrl);
            System.out.println("Max Pages: " + maxPages);
            System.out.println("Delay: " + delay + "ms");
            System.out.println("Headless: " + headless);
            System.out.println("LambdaTest: " + useLambdaTest);
            System.out.println("Console Errors: " + checkConsoleErrors);
            System.out.println("Detailed Errors: " + useDetailedErrorTracking);
            System.out.println("Broken Images: " + checkBrokenImages);
            System.out.println("============================\n");
            // Create and run crawler
            SeleniumWebCrawler_v6 crawler = new SeleniumWebCrawler_v6(
                    startUrl, maxPages, delay, headless,
                    checkConsoleErrors, useDetailedErrorTracking,
                    checkBrokenImages, useLambdaTest);
            try {
                crawler.crawl();
            } catch (Exception e) {
                System.err.println("Crawling failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
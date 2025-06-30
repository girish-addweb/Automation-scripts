/**
 * ConsoleErrorLogger.java
 *
 * A utility that crawls the pages listed in one or more XML sitemaps, captures:
 *   • JavaScript console errors (SEVERE / WARNING)
 *   • Network‑layer errors (HTTP status ≥ 400)
 *
 * It then exports the aggregated results to:
 *   • Excel  (.xlsx) – two sheets: “Console Errors” and “Network Errors”
 *   • CSV    (.csv)  – flat list of all errors
 *   • HTML   (.html) – simple, share‑ready report
 *
 * Key technologies
 *   • Selenium WebDriver + ChromeDriver (headless capable)
 *   • Apache POI (Excel writing)
 *   • org.json (parsing Chrome DevTools “performance” log)
 *   • DOM parser (javax.xml) for reading <loc> values from the sitemap
 *
 * Usage
 *   1. Place the ChromeDriver binary in your project root (or adjust the path).
 *   2. Edit the `sitemapUrls` list below to target your own XML sitemaps.
 *   3. Run `java ConsoleErrorLogger`. Reports are created alongside your project.
 *
 * Author : Girish Teli
 * Created: 30 Jun 2025
 */
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.*;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;

import org.json.JSONObject;

public class ConsoleErrorLogger {

    /* ───────────────────────────── Entry Point ───────────────────────────── */

    /**
     * Launches Chrome (headless), iterates through every page found in the given
     * sitemap(s), logs errors, and writes the three output reports.
     */
    public static void main(String[] args) {
        /* 1️⃣  Configure ChromeDriver path (assumes driver is in project root). */
        System.setProperty("Webdriver.chrome.driver",
                System.getProperty("user.dir") + "/chromedriver");

        /* 2️⃣  Build ChromeOptions – headless & full log capture. */
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");           // comment‑out to see UI
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        LoggingPreferences logs = new LoggingPreferences();
        logs.enable(LogType.BROWSER,     Level.ALL);      // console logs
        logs.enable(LogType.PERFORMANCE, Level.ALL);      // DevTools logs
        options.setCapability("goog:loggingPrefs", logs);

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        /* 3️⃣  Target sitemap(s) – add/remove as needed. */
        List<String> sitemapUrls = Arrays.asList(
                "https://www.silverfort.com/sitemap.xml"
                // "https://example.com/another-sitemap.xml"
        );

        /* 4️⃣  Output file paths. */
        String excelFilePath = System.getProperty("user.dir") + "/Console_Error_Report.xlsx";
        String csvFilePath   = System.getProperty("user.dir") + "/Console_Error_Report.csv";
        String htmlFilePath  = System.getProperty("user.dir") + "/Console_Error_Report.html";

        /* 5️⃣  Prepare Excel workbook & sheets + CSV/HTML working list. */
        Workbook workbook     = new XSSFWorkbook();
        Sheet consoleSheet    = workbook.createSheet("Console Errors");
        Sheet networkSheet    = workbook.createSheet("Network Errors");

        // Header rows for Excel
        Row headerRow1 = consoleSheet.createRow(0);
        headerRow1.createCell(0).setCellValue("Page URL");
        headerRow1.createCell(1).setCellValue("Error Type");
        headerRow1.createCell(2).setCellValue("Error Message");

        Row headerRow2 = networkSheet.createRow(0);
        headerRow2.createCell(0).setCellValue("Page URL");
        headerRow2.createCell(1).setCellValue("Status Code");
        headerRow2.createCell(2).setCellValue("Request URL");

        // CSV / HTML records (first row = header)
        List<String[]> errorRecords = new ArrayList<>();
        errorRecords.add(new String[]{"Page URL", "Error Type", "Error Message"});

        int rowNumConsole = 1;
        int rowNumNetwork = 1;

        /* 6️⃣  Crawl each sitemap, then each page within. */
        for (String sitemapUrl : sitemapUrls) {
            List<String> pageUrls = extractUrlsFromSitemap(sitemapUrl);
            if (pageUrls.isEmpty()) {
                System.out.println("⚠️  No URLs found in sitemap: " + sitemapUrl);
                continue;
            }

            for (String pageUrl : pageUrls) {
                System.out.println("\n🔍 Visiting: " + pageUrl);
                try {
                    driver.get(pageUrl);
                    Thread.sleep(4000);          // wait for async JS / network
                } catch (Exception e) {
                    System.out.println("❌ Error loading page: " + pageUrl);
                    continue;                    // skip to next URL
                }

                // Capture console & network errors
                rowNumConsole = logConsoleErrors(driver, pageUrl,
                        consoleSheet, rowNumConsole, errorRecords);
                rowNumNetwork = logNetworkErrors(driver, pageUrl,
                        networkSheet, rowNumNetwork, errorRecords);
            }
        }

        /* 7️⃣  Persist reports to disk. */
        saveExcelReport(workbook, excelFilePath);
        saveCsvReport(errorRecords, csvFilePath);
        saveHtmlReport(errorRecords, htmlFilePath);

        /* 8️⃣  Shutdown WebDriver. */
        driver.quit();
    }

    /* ─────────────────────── Sitemap Helper  ─────────────────────── */

    /**
     * Reads `<loc>` nodes from the given XML sitemap.
     *
     * @param sitemapUrl Absolute URL to a sitemap (XML).
     * @return List of page URLs or an empty list on failure.
     */
    public static List<String> extractUrlsFromSitemap(String sitemapUrl) {
        List<String> urls = new ArrayList<>();
        try {
            URL url = new URL(sitemapUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Parse XML using DOM
            InputStream inputStream = connection.getInputStream();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder  = factory.newDocumentBuilder();
            Document document        = builder.parse(inputStream);
            inputStream.close();

            // Collect all <loc> tags
            NodeList nodeList = document.getElementsByTagName("loc");
            for (int i = 0; i < nodeList.getLength(); i++) {
                urls.add(nodeList.item(i).getTextContent());
            }
        } catch (Exception e) {
            System.out.println("❌ Error fetching/parsing sitemap: " + e.getMessage());
        }
        return urls;
    }

    /* ─────────────────────── Console‑Error Helper  ─────────────────────── */

    /**
     * Records SEVERE/WARNING console logs for the current page.
     *
     * @param driver       Active WebDriver instance.
     * @param url          URL of the page just visited (for context).
     * @param sheet        Excel sheet to write into.
     * @param rowNum       Current Excel row pointer (will be incremented).
     * @param errorRecords In‑memory list for CSV/HTML.
     * @return Updated row pointer.
     */
    public static int logConsoleErrors(WebDriver driver,
                                       String url,
                                       Sheet sheet,
                                       int rowNum,
                                       List<String[]> errorRecords) {

        LogEntries logEntries = driver.manage().logs().get(LogType.BROWSER);
        boolean firstErrorForUrl = true;          // needed for row merging
        int startRow = rowNum;

        for (LogEntry entry : logEntries) {
            // Normalise message for pattern matching
            String message   = entry.getMessage().toLowerCase();
            String errorType = determineErrorType(message);

            if (entry.getLevel() == Level.SEVERE || entry.getLevel() == Level.WARNING) {
                System.out.println("🚨 [Console - " + errorType + "] " + entry.getMessage());

                Row row = sheet.createRow(rowNum++);
                // only write URL in the first row for this page, then merge later
                row.createCell(0).setCellValue(firstErrorForUrl ? url : "");
                row.createCell(1).setCellValue(errorType);
                row.createCell(2).setCellValue(entry.getMessage());

                errorRecords.add(new String[]{
                        firstErrorForUrl ? url : "", errorType, entry.getMessage()});

                firstErrorForUrl = false;
            }
        }

        // Merge URL cell vertically so it spans all error rows for that page
        if (startRow < rowNum - 1) {
            sheet.addMergedRegion(new CellRangeAddress(startRow, rowNum - 1, 0, 0));
        }
        return rowNum;
    }

    /* ─────────────────────── Network‑Error Helper  ─────────────────────── */

    /**
     * Parses Chrome DevTools performance log for failed HTTP responses.
     *
     * @param driver       Active WebDriver instance.
     * @param url          URL of the page just visited (for context).
     * @param sheet        Excel “Network Errors” sheet.
     * @param rowNum       Current Excel row pointer.
     * @param errorRecords Shared list for CSV/HTML output.
     * @return Updated row pointer.
     */
    public static int logNetworkErrors(WebDriver driver,
                                       String url,
                                       Sheet sheet,
                                       int rowNum,
                                       List<String[]> errorRecords) {

        LogEntries logs = driver.manage().logs().get(LogType.PERFORMANCE);

        for (LogEntry entry : logs) {
            try {
                JSONObject logJson = new JSONObject(entry.getMessage());
                JSONObject message = logJson.getJSONObject("message");

                /* We only care about Network.responseReceived events. */
                if (!message.has("method") ||
                        !message.getString("method").equals("Network.responseReceived")) {
                    continue;
                }

                JSONObject response = message.getJSONObject("params").getJSONObject("response");
                int status          = response.getInt("status");
                String requestUrl   = response.getString("url");

                if (status >= 400) {
                    System.out.println("❌ [Network] " + requestUrl + " → Status: " + status);

                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(url);
                    row.createCell(1).setCellValue(status);
                    row.createCell(2).setCellValue(requestUrl);

                    errorRecords.add(new String[]{
                            url, "Network Error " + status, requestUrl});
                }

            } catch (Exception ignored) {
                /* Malformed or unexpected log entry – safely skip. */
            }
        }
        return rowNum;
    }

    /* ─────────────────────── Utility Helpers  ─────────────────────── */

    /**
     * Heuristic classifier that maps a console‑log message to an error bucket.
     */
    public static String determineErrorType(String message) {
        if (message.contains("content security policy") || message.contains("violation"))
            return "CSP Error";
        else if (message.contains("network"))
            return "Network Error";
        else if (message.contains("javascript") || message.contains("uncaught typeerror"))
            return "JavaScript Error";
        else if (message.contains("security"))
            return "Security Error";
        else if (message.contains("failed") || message.contains("404"))
            return "Failed Request Error";
        else
            return "General Console Error";
    }

    /**
     * Writes the populated Apache POI Workbook to disk.
     */
    public static void saveExcelReport(Workbook workbook, String filePath) {
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            workbook.close();
            System.out.println("✅ Excel report saved at: " + filePath);
        } catch (Exception e) {
            System.out.println("❌ Error saving Excel file: " + e.getMessage());
        }
    }

    /**
     * Writes the combined error list to a simple CSV file.
     */
    public static void saveCsvReport(List<String[]> records, String filePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            for (String[] record : records) {
                writer.write(String.join(",", record));
                writer.newLine();
            }
            System.out.println("✅ CSV report saved at: " + filePath);
        } catch (Exception e) {
            System.out.println("❌ Error saving CSV file: " + e.getMessage());
        }
    }

    /**
     * Renders the error list as an HTML table with basic styling.
     */
    public static void saveHtmlReport(List<String[]> records, String filePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            writer.write("<html><head><title>Console + Network Error Report</title>");
            writer.write("<style>table{border-collapse:collapse;}th,td{border:1px solid #000;padding:8px;}</style>");
            writer.write("</head><body>");
            writer.write("<h2>Error Report</h2><table>");
            writer.write("<tr><th>Page URL</th><th>Error Type</th><th>Error Message</th></tr>");

            // Skip header row in records[0]
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                writer.write("<tr>");
                writer.write("<td>" + (record[0].isEmpty() ? "&nbsp;" : record[0]) + "</td>");
                writer.write("<td>" + record[1] + "</td>");
                writer.write("<td>" + record[2] + "</td>");
                writer.write("</tr>");
            }

            writer.write("</table></body></html>");
            System.out.println("✅ HTML report saved at: " + filePath);
        } catch (Exception e) {
            System.out.println("❌ Error saving HTML file: " + e.getMessage());
        }
    }
}
C:\Users\addweb\IDEAProjects\digibee-link\src\test\java\ConsoleErrorLogger.java
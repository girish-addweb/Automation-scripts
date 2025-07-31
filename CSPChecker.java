import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
public class CSPChecker {
    static WebDriver driver;
    static Workbook workbook;
    static Sheet sheet;
    static int rowNum;

    public static void main(String[] args) {

        Runnable task = () -> {
            try {
                setupDriver();
                runCSPCheck();
                driver.quit();
            } catch (Exception e) {
                System.out.println("❌ Scheduler error: " + e.getMessage());
            }
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(task, 0, 40, TimeUnit.HOURS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }));
    }
    public static void setupDriver() {
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "/chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        org.openqa.selenium.logging.LoggingPreferences logPrefs = new org.openqa.selenium.logging.LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, java.util.logging.Level.ALL);
        options.setCapability("goog:loggingPrefs", logPrefs);
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        // Reinitialize workbook each time
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet("CSP_Errors");
        rowNum = 0;
    }
    public static void runCSPCheck() throws Exception {
        createHeader();
        List<String> sitemapUrls = Arrays.asList(
                "https://www.a-lign.com/post-sitemap.xml",
                "https://www.a-lign.com/page-sitemap.xml",
                "https://www.a-lign.com/resource-sitemap.xml",
                "https://www.a-lign.com/people-sitemap.xml",
                "https://www.a-lign.com/service-sitemap.xml",
                "https://www.a-lign.com/testimonials-sitemap.xml",
                "https://www.a-lign.com/geo-location-sitemap.xml",
                "https://www.a-lign.com/integration-type-sitemap.xml"
        );
        for (String sitemapUrl : sitemapUrls) {
            List<String> pageUrls = readUrlsFromSitemap(sitemapUrl);
            for (String pageUrl : pageUrls) {
                checkCSPForURL(pageUrl);
            }
        }
        // Save file with timestamp
        String fileName = "CSP_Errors_" + System.currentTimeMillis() + ".xlsx";
        FileOutputStream out = new FileOutputStream(fileName);
        workbook.write(out);
        out.close();
        System.out.println("✅ CSP report saved: " + fileName);
    }
    public static void createHeader() {
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("URL");
        headerRow.createCell(1).setCellValue("CSP Error Message");
        headerRow.createCell(2).setCellValue("Timestamp");
    }
    public static List<String> readUrlsFromSitemap(String sitemapUrl) {
        List<String> urls = new ArrayList<>();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(sitemapUrl).openConnection();
            connection.setRequestMethod("GET");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(connection.getInputStream());
            NodeList nList = doc.getElementsByTagName("loc");
            for (int i = 0; i < nList.getLength(); i++) {
                urls.add(nList.item(i).getTextContent().trim());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error reading sitemap: " + sitemapUrl);
        }
        return urls;
    }
    public static void checkCSPForURL(String pageUrl) {
        try {
            driver.get(pageUrl);
            Thread.sleep(2000); // wait for logs to load
            LogEntries logs = driver.manage().logs().get(LogType.BROWSER);
            boolean cspErrorFound = false;
            for (LogEntry entry : logs) {
                String message = entry.getMessage().toLowerCase();
                if (message.contains("content security policy") ||
                        message.contains("csp") ||
                        message.contains("refused to") ||
                        message.contains("violat") ||
                        message.contains("blocked")) {
                    writeErrorToExcel(pageUrl, entry.getMessage());
                    cspErrorFound = true;
                }
            }
            if (!cspErrorFound) {
                System.out.println("✅ No CSP error: " + pageUrl);
            }
        } catch (Exception e) {
            System.out.println("❌ Error accessing: " + pageUrl + " - " + e.getMessage());
        }
    }
    public static void writeErrorToExcel(String url, String error) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(url);
        row.createCell(1).setCellValue(error);
        row.createCell(2).setCellValue(new Date().toString());
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i <= 2; i++) {
            row.getCell(i).setCellStyle(style);
        }
        System.out.println("❌ CSP Error found on: " + url);
    }
}
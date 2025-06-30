import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Apache POI imports
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class FontVerification {

    public static void main(String[] args) throws Exception {
        // Set up WebDriver (update the path to your chromedriver as needed)
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "chromedriver");
        WebDriver driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        // Create Excel workbook and sheet for failures
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Failures");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Page URL");
        headerRow.createCell(1).setCellValue("Heading Tag");
        headerRow.createCell(2).setCellValue("Heading Text");
        headerRow.createCell(3).setCellValue("Font Family");

        int excelRow = 1; // starting row for data

        // Sitemap URL
        String sitemapUrl = "https://www.voltactivedata.com/press_release-sitemap.xml";

        // Fetch URLs from sitemap
        List<String> pageUrls = getUrlsFromSitemap(sitemapUrl);

        // Loop through each URL
        for (String url : pageUrls) {
            driver.get(url);

            // Find the main content container, try <main> then fallback to div#content
            WebElement mainContent;
            try {
                mainContent = driver.findElement(By.tagName("main"));
            } catch (NoSuchElementException e) {
                try {
                    mainContent = driver.findElement(By.cssSelector("div#content"));
                } catch (NoSuchElementException ex) {
                    System.out.println("⚠️ Skipping " + url + " — no <main> or #content found.");
                    continue;
                }
            }

            // Loop through each heading (h1 to h6) inside the main content only
            for (int i = 1; i <= 6; i++) {
                List<WebElement> headings = mainContent.findElements(By.tagName("h" + i));
                for (WebElement heading : headings) {
                    String headingText = heading.getText().trim();
                    if (headingText.isEmpty()) {
                        headingText = "[No Text]";
                    }

                    String fontFamily = heading.getCssValue("font-family");

                    if (fontFamily.toLowerCase().contains("raleway")) {
                        System.out.println("✔️ PASS: <h" + i + "> \"" + headingText
                                + "\" is using Raleway on " + url);
                    } else {
                        System.out.println("❌ FAIL: <h" + i + "> \"" + headingText
                                + "\" is NOT using Raleway (found: " + fontFamily + ") on " + url);

                        // Write failure details to Excel
                        Row row = sheet.createRow(excelRow++);
                        row.createCell(0).setCellValue(url);
                        row.createCell(1).setCellValue("h" + i);
                        row.createCell(2).setCellValue(headingText);
                        row.createCell(3).setCellValue(fontFamily);
                    }
                }
            }
        }

        driver.quit();

        // Save the Excel file with failure details
        try (FileOutputStream fileOut = new FileOutputStream("FontVerificationFailures.xlsx")) {
            workbook.write(fileOut);
        }
        workbook.close();
        System.out.println("\n✅ Verification complete. Failure details saved to FontVerificationFailures.xlsx");
    }

    // Method to parse sitemap.xml and extract page URLs
    public static List<String> getUrlsFromSitemap(String sitemapUrl) throws Exception {
        List<String> urls = new ArrayList<>();
        URL url = new URL(sitemapUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(conn.getInputStream());

        NodeList locNodes = doc.getElementsByTagName("loc");
        for (int i = 0; i < locNodes.getLength(); i++) {
            urls.add(locNodes.item(i).getTextContent());
        }
        return urls;
    }
}

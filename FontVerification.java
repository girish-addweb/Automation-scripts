import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FontVerification {
    // Old font stack (normalized for comparison)
    private static final String OLD_FONT = "neue montreal, sans-serif";

    public static void main(String[] args) throws Exception {
        // 1. Setup ChromeDriver
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "/chromedriver");

        WebDriver driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        // 2. Prepare Excel workbook for failures
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Failures");
        String[] headers = {
                "Page URL",
                "Tag",
                "Identifier",
                "Text Snippet",
                "Font Family"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        int excelRow = 1;

        // 3. All staging sitemap URLs
        String[] sitemapUrls = {
//                "https://theorangebyte.addwebprojects.com/post-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/page-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/services-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/success-stories-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/post_tag-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/technologies-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/industries-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/success_tag-sitemap.xml"

                "https://theorangebyte.com/post-sitemap.xml",
                "https://theorangebyte.com/page-sitemap.xml",
                "https://theorangebyte.com/services-sitemap.xml",
                "https://theorangebyte.com/success-stories-sitemap.xml",
                "https://theorangebyte.com/success_tag-sitemap.xml"
        };

        // 4. Aggregate all page URLs
        List<String> allPageUrls = new ArrayList<>();
        for (String sitemap : sitemapUrls) {
            allPageUrls.addAll(getUrlsFromSitemap(sitemap));
        }

        // 5. Tags to inspect
        String[] tagsToCheck = {
                "h1", "h2", "h3", "h4", "h5", "h6",
                "p", "span", "a", "li", "td", "th",
                "button", "label"
        };

        // 6. Iterate pages and verify fonts
        for (String url : allPageUrls) {
            driver.get(url);
            System.out.println("▶️ Testing page: " + url);

            for (String tag : tagsToCheck) {
                List<WebElement> elements = driver.findElements(By.tagName(tag));
                for (WebElement elem : elements) {
                    String text = elem.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }

                    // Normalize computed font-family
                    String fontFamily = elem.getCssValue("font-family")
                            .toLowerCase()
                            .replaceAll("\"", "")
                            .replaceAll("\\s+", " ");

                    // If current font uses the OLD font, it is a failure
                    if (fontFamily.contains(OLD_FONT)) {
                        System.out.println("❌ OLD FONT FOUND: <" + tag + "> \""
                                + (text.length() > 30 ? text.substring(0, 27) + "…" : text)
                                + "\" font=" + fontFamily);

                        // Safe identifier fallback logic
                        String id = elem.getAttribute("id");
                        String cls = elem.getAttribute("class");
                        String identifier = (id != null && !id.isEmpty())
                                ? "#" + id
                                : (cls != null && !cls.isEmpty())
                                ? "." + cls
                                : "[no-id/class]";

                        // Write failure to Excel
                        Row row = sheet.createRow(excelRow++);
                        row.createCell(0).setCellValue(url);
                        row.createCell(1).setCellValue(tag);
                        row.createCell(2).setCellValue(identifier);
                        row.createCell(3).setCellValue(text.length() > 50
                                ? text.substring(0, 47) + "…" : text);
                        row.createCell(4).setCellValue(fontFamily);
                    } else {
                        System.out.println("✔️ NO OLD FONT: <" + tag + "> \""
                                + (text.length() > 30 ? text.substring(0, 27) + "…" : text)
                                + "\"");
                    }
                }
            }
        }

        // 7. Tear down and save
        driver.quit();
        try (FileOutputStream out =
                     new FileOutputStream("FontVerificationFailures.xlsx")) {
            workbook.write(out);
        }
        workbook.close();
        System.out.println("✅ Verification complete. Failures saved to FontVerificationFailures.xlsx");
    }

    // Parses a sitemap XML and returns all <loc> URLs
    public static List<String> getUrlsFromSitemap(String sitemapUrl)
            throws Exception {
        List<String> urls = new ArrayList<>();
        URL url = new URL(sitemapUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(conn.getInputStream());
        NodeList locNodes = doc.getElementsByTagName("loc");
        for (int i = 0; i < locNodes.getLength(); i++) {
            urls.add(locNodes.item(i).getTextContent().trim());
        }
        return urls;
    }
}

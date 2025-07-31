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

public class FontsAudit {
    public static void main(String[] args) throws Exception {
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "/chromedriver");

        WebDriver driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        // Prepare Excel workbook and sheet
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("FontsAudit");
        String[] headers = {"Page URL", "Tag", "Identifier", "Text Snippet", "Font Family"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        int excelRow = 1;

        // Sitemap URLs — same as before or modify your list here
        String[] sitemapUrls = {
//                "https://theorangebyte.addwebprojects.com/post-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/page-sitemap.xml",
                "https://theorangebyte.addwebprojects.com/services-sitemap.xml"
//                "https://theorangebyte.addwebprojects.com/success-stories-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/post_tag-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/technologies-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/industries-sitemap.xml",
//                "https://theorangebyte.addwebprojects.com/success_tag-sitemap.xml"
        };

        List<String> allPageUrls = new ArrayList<>();
        for (String sitemap : sitemapUrls) {
            allPageUrls.addAll(getUrlsFromSitemap(sitemap));
        }

        // Tags to check - headings + common text tags
        String[] tagsToCheck = {
                "h1","h2","h3","h4","h5","h6",
                "p","span","a","li","td","th",
                "button","label"
        };

        for (String url : allPageUrls) {
            driver.get(url);
            System.out.println("⏳ Auditing fonts on: " + url);

            for (String tag : tagsToCheck) {
                List<WebElement> elements = driver.findElements(By.tagName(tag));
                for (WebElement elem : elements) {
                    String text = elem.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }

                    // Get computed font-family, normalize
                    String fontFamily = elem.getCssValue("font-family")
                            .toLowerCase()
                            .replaceAll("\"", "")
                            .replaceAll("\\s+", " ");

                    // Get element identifier
                    String id = elem.getAttribute("id");
                    String cls = elem.getAttribute("class");
                    String identifier = (id != null && !id.isEmpty()) ? "#" + id
                            : (cls != null && !cls.isEmpty()) ? "." + cls
                            : "[no-id/class]";

                    // Write row to Excel
                    Row row = sheet.createRow(excelRow++);
                    row.createCell(0).setCellValue(url);
                    row.createCell(1).setCellValue(tag);
                    row.createCell(2).setCellValue(identifier);
                    row.createCell(3).setCellValue(text.length() > 50 ? text.substring(0, 47) + "…" : text);
                    row.createCell(4).setCellValue(fontFamily);
                }
            }
        }

        driver.quit();

        try (FileOutputStream out = new FileOutputStream("FontsAuditReport.xlsx")) {
            workbook.write(out);
        }
        workbook.close();
        System.out.println("✅ Fonts audit complete. Report saved as FontsAuditReport.xlsx");
    }

    // Parses sitemap XML to get URLs
    public static List<String> getUrlsFromSitemap(String sitemapUrl) throws Exception {
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

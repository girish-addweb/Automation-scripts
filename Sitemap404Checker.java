import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Sitemap404Checker {
    public static void main(String[] args) {
        List<String> sitemapUrls = Arrays.asList(
//                "https://bdfgraphics.addwebprojects.com/product-sitemap3.xml",
//                "https://bdfgraphics.addwebprojects.com/product-sitemap4.xml",
//                "https://bdfgraphics.addwebprojects.com/product-sitemap5.xml",
//                "https://bdfgraphics.addwebprojects.com/product-sitemap6.xml",
//                "https://bdfgraphics.addwebprojects.com/category-sitemap.xml",
//                "https://bdfgraphics.addwebprojects.com/product_cat-sitemap1.xml",
//                "https://bdfgraphics.addwebprojects.com/product_cat-sitemap2.xml",
//                "https://bdfgraphics.addwebprojects.com/local-sitemap.xml"

                "https://strategicadvisersllc.com/post-sitemap.xml",
        "https://strategicadvisersllc.com/page-sitemap.xml",
        "https://strategicadvisersllc.com/essential_grid-sitemap.xml",
        "https://strategicadvisersllc.com/category-sitemap.xml",
        "https://strategicadvisersllc.com/post_tag-sitemap.xml"
        );

        if (sitemapUrls.isEmpty()) {
            System.out.println("❌ No sitemap URLs provided.");
            return;
        }

        // Excel Setup
        String excelFilePath = System.getProperty("user.dir") + "/Full_URL_Status_Report.xlsx";
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("URL Status");

        // Header Row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Sitemap");
        headerRow.createCell(1).setCellValue("Page URL");
        headerRow.createCell(2).setCellValue("HTTP Status");

        int rowNum = 1;

        for (String sitemapUrl : sitemapUrls) {
            System.out.println("🔍 Parsing sitemap: " + sitemapUrl);
            List<String> pageUrls = extractUrlsFromSitemap(sitemapUrl);
            for (String pageUrl : pageUrls) {
                int status = getHttpStatusCode(pageUrl);

                // Write to Excel
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sitemapUrl);
                row.createCell(1).setCellValue(pageUrl);
                row.createCell(2).setCellValue(status);

                // Console feedback
                if (status == 404) {
                    System.out.println("❌ 404 Not Found: " + pageUrl);
                } else {
                    System.out.println("✅ " + status + ": " + pageUrl);
                }
            }
        }

        // Save Excel
        try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
            workbook.write(fileOut);
            System.out.println("\n📄 Excel report saved to: " + excelFilePath);
        } catch (Exception e) {
            System.out.println("❌ Error saving Excel file: " + e.getMessage());
        }

        try {
            workbook.close();
        } catch (Exception e) {
            System.out.println("❌ Error closing workbook: " + e.getMessage());
        }
    }

    // Extract URLs from sitemap
    public static List<String> extractUrlsFromSitemap(String sitemapUrl) {
        List<String> urls = new ArrayList<>();
        try {
            URL url = new URL(sitemapUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream inputStream = connection.getInputStream();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            inputStream.close();

            NodeList locNodes = doc.getElementsByTagName("loc");
            for (int i = 0; i < locNodes.getLength(); i++) {
                String loc = locNodes.item(i).getTextContent().trim();
                urls.add(loc);
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to parse sitemap: " + sitemapUrl + " - " + e.getMessage());
        }
        return urls;
    }

    // Get HTTP status of a URL
    public static int getHttpStatusCode(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            return conn.getResponseCode();
        } catch (Exception e) {
            System.out.println("❌ Error fetching status for URL: " + urlStr + " - " + e.getMessage());
            return -1;
        }
    }
}

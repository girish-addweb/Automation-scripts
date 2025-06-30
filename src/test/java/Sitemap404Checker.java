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
import java.util.ArrayList;
import java.util.List;

public class Sitemap404Checker {
    public static void main(String[] args) {
        String sitemapUrl = "https://www.ocbound.com/attachment-sitemap.xml";
        List<String> urls = extractUrlsFromSitemap(sitemapUrl);

        if (urls.isEmpty()) {
            System.out.println("❌ No URLs found in the sitemap.");
            return;
        }

        // Create Excel file for logging 404 errors
        String excelFilePath = System.getProperty("user.dir") + "/404_Error_Report-post.xlsx";
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("404 Errors");

        // Create header row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Page URL");
        headerRow.createCell(1).setCellValue("Status Code");

        int rowNum = 1;
        for (String url : urls) {
            int statusCode = getHttpStatusCode(url);
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(url);
            row.createCell(1).setCellValue(statusCode);

            if (statusCode == 404) {
                System.out.println("❌ 404 Not Found: " + url);
            }
        }

        // Save Excel file
        try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
            workbook.write(fileOut);
            System.out.println("\n✅ 404 error report saved at: " + excelFilePath);
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
            Document document = builder.parse(inputStream);
            inputStream.close();

            NodeList nodeList = document.getElementsByTagName("loc");
            for (int i = 0; i < nodeList.getLength(); i++) {
                urls.add(nodeList.item(i).getTextContent());
            }
        } catch (Exception e) {
            System.out.println("❌ Error fetching/parsing sitemap: " + e.getMessage());
        }
        return urls;
    }

    // Get HTTP status code of a URL
    public static int getHttpStatusCode(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            return connection.getResponseCode();
        } catch (Exception e) {
            System.out.println("❌ Error checking URL: " + urlString + " - " + e.getMessage());
            return -1;
        }
    }
}

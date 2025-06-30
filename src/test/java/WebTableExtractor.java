import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class WebTableExtractor {

    public static void main(String[] args) throws IOException {
        // Set up ChromeDriver
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "chromedriver");
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Excel setup
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Extracted Data");
        int rowCount = 0;

        try {
            driver.get("https://europe.wordcamp.org/2025/attendees/"); // Replace with the actual page URL
            Thread.sleep(2000); // Let page load

            for (int i = 1; i <= 2; i++) {
                String linkXPath = "/html/body/div[1]/div[2]/div/div/div/ul/li[" + i + "]/a[1]";
                String tableXPath = "/html/body/div[2]/div/div/div[1]/div/div[1]/ul";

                System.out.println("Trying to find section " + i);

                try {
                    WebElement sectionLink = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(linkXPath)));
                    String sectionName = sectionLink.getText().isEmpty() ? "Section " + i : sectionLink.getText();

                    sectionLink.click();
                    Thread.sleep(1000); // wait for table to load

                    // Write section name
                    Row sectionRow = sheet.createRow(rowCount++);
                    sectionRow.createCell(0).setCellValue(sectionName);

                    try {
                        List<WebElement> tableItems = driver.findElements(By.xpath(tableXPath));
                        if (tableItems.isEmpty()) {
                            Row row = sheet.createRow(rowCount++);
                            row.createCell(0).setCellValue("table is not appear");
                        } else {
                            List<WebElement> listItems = driver.findElements(By.xpath(tableXPath + "/li"));
                            for (WebElement item : listItems) {
                                Row row = sheet.createRow(rowCount++);
                                row.createCell(0).setCellValue(item.getText().trim());
                            }
                        }
                    } catch (NoSuchElementException e) {
                        Row row = sheet.createRow(rowCount++);
                        row.createCell(0).setCellValue("table is not appear");
                    }

                    rowCount++; // space after each section

                } catch (Exception e) {
                    System.out.println("Section " + i + " not found.");
                    Row row = sheet.createRow(rowCount++);
                    row.createCell(0).setCellValue("Section " + i + " not found.");
                    rowCount++;
                }
            }

            // Save Excel file
            FileOutputStream outputStream = new FileOutputStream("TableData.xlsx");
            workbook.write(outputStream);
            workbook.close();
            outputStream.close();
            System.out.println("Data written to TableData.xlsx");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}

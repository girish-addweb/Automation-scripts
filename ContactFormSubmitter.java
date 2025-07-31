import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.time.Duration;

public class ContactFormSubmitter {

    public static void main(String[] args) {
        String name = "Girish-Addweb";
        String email = "johnnyharpertesting20@gmail.com";
        String phone = "9157382201";
        String description = "This is for the testing.";

        if (name == null || name.equalsIgnoreCase("Name") || name.trim().isEmpty()) {
            System.out.println("Skipping header row or empty data");
            return;
        }

        System.setProperty("Webdriver.chrome.driver",
                System.getProperty("user.dir") + "/chromedriver");
        WebDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get("https://addwebsolutionstaging.mystagingwebsite.com/contact-us");
            System.out.println("Page loaded for user: " + name);

            WebElement nameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("your-name")));
            nameField.clear();
            nameField.sendKeys(name);

            WebElement emailField = driver.findElement(By.name("email"));
            emailField.clear();
            emailField.sendKeys(email);

            WebElement phoneField = driver.findElement(By.name("phone"));
            phoneField.clear();
            phoneField.sendKeys(phone);

            WebElement messageField = driver.findElement(By.name("message"));
            messageField.clear();
            messageField.sendKeys(description);

            Thread.sleep(1000); // Optional delay

            WebElement submitButton = driver.findElement(By.cssSelector("input.wpcf7-submit"));
            submitButton.click();

            System.out.println("Form submitted for user: " + name);

            // Wait for either a Thank You page redirect OR success message
            Thread.sleep(3000); // wait for possible redirection

            // Check if URL changed to Thank You page (update this URL if needed)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("thank-you"),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".wpcf7-response-output"))
            ));

            // Capture screenshot after form submission/redirect
            takeScreenshot(driver, "thank-you-" + name);

        } catch (Exception e) {
            System.err.println("Error processing form for user " + name + ": " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    private static void takeScreenshot(WebDriver driver, String fileNamePrefix) {
        try {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String filename = fileNamePrefix + "-" + timestamp + ".png";
            File destFile = new File(System.getProperty("user.dir") + "/screenshots/" + filename);
            destFile.getParentFile().mkdirs(); // Create directory if not exists
            Files.copy(scrFile.toPath(), destFile.toPath());
            System.out.println("Screenshot saved: " + destFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }
}

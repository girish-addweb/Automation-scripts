import com.applitools.eyes.config.Configuration;
import com.applitools.eyes.selenium.BrowserType;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.visualgrid.model.DeviceName;
import com.applitools.eyes.visualgrid.model.ScreenOrientation;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;

public class VisualTest {
    public static void main(String[] args) {
        Eyes eyes = new Eyes();
        WebDriver driver = null;

        try {
            // 1. Set API key (replace with your real Applitools key)
            eyes.setApiKey("uNFnIh7sP0lifvSiXDcPQpwOAL2F5Vjjr1JoSuBmqI0110");

            // 2. Configure Ultrafast Grid browsers/devices BEFORE eyes.open()
            Configuration config = new Configuration();
            config.addBrowser(1200, 800, BrowserType.CHROME);
            config.addBrowser(1200, 800, BrowserType.FIREFOX);
            config.addBrowser(1200, 800, BrowserType.SAFARI);
            config.addDeviceEmulation(DeviceName.iPhone_X, ScreenOrientation.PORTRAIT);
            eyes.setConfiguration(config);

            // 3. Start ChromeDriver
            driver = new ChromeDriver();

            // 4. Open Eyes session; do NOT reassign driver unless SDK requires (most do not)
            eyes.open(driver, "Volttest - Responsive", "homepage-iPhone-x", new RectangleSize(375, 812));

            // 5. Navigate to test site
            driver.get("https://www.voltactivedata.com/");

            // 6. Wait for page URL and elements
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.urlContains("voltactivedata"));
            System.out.println("Page title: " + driver.getTitle());
            System.out.println("Current URL: " + driver.getCurrentUrl());

            // 7. Accept cookie consent dialog if present
            try {
                WebElement acceptBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("/html/body/div[6]/div[2]/div/div[1]/div/div[2]/div/button[3]")
                ));
                acceptBtn.click();
            } catch (Exception ignore) {
                // If accept button not found, continue; not all page visits will trigger it
            }

            // 8. Wait for the header element after handling consent
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("/html/body/div[1]/div/header/div")));

            // 9. Visual checkpoint: full page
            eyes.check("HomePage (full)", Target.window().fully());

            // 10. Visual checkpoint: header region (fresh lookup)
            WebElement header = driver.findElement(By.xpath("/html/body/div[1]/div/header/div"));
            eyes.check("Header section", Target.region(header));

            // 11. Close Eyes and retrieve test results
            TestResults results = eyes.close(false);

            System.out.println("Applitools Test Session URL: " + results.getAppUrls().getSession());
            System.out.println("Visual Differences Found? " + results.isDifferent());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Always abort Eyes if not properly closed and quit driver
            eyes.abortIfNotClosed();
            if (driver != null) {
                driver.quit();
            }
        }
    }
}

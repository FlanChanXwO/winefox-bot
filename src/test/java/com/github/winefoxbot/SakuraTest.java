package com.github.winefoxbot;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-25-21:21
 */
public class SakuraTest {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch( new BrowserType.LaunchOptions()
                    .setExecutablePath(Paths.get("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"))
                    .setHeadless(false))) {
                Browser.NewPageOptions newPageOptions = new Browser.NewPageOptions();
                newPageOptions.baseURL = "https://www.857fans.com/";
                Page page = browser.newPage(newPageOptions);
                page.navigate("https://www.857fans.com/");
                page.waitForLoadState();
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
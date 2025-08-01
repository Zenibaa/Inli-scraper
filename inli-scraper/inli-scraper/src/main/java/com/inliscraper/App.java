package com.inliscraper;

import java.util.TimeZone;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
        InliScraper scraper = new InliScraper();
        scraper.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scraper.stop();
        }));
    }
}

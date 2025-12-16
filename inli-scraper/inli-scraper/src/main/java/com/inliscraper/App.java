package com.inliscraper;

import io.javalin.Javalin;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TimeZone;

public class App {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));
        
        // Démarre le scraper
        InliScraper scraper = new InliScraper();
        scraper.start();
        
        // Démarre le serveur HTTP pour Railway
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);
        
        System.out.println("🌐 Serveur HTTP démarré sur le port " + port);
        
        // Endpoint de santé pour Railway
        app.get("/", ctx -> {
            ctx.json(Map.of(
                "status", "running",
                "service", "Inli Scraper",
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        });
        
        // Endpoint de health check
        app.get("/health", ctx -> {
            ctx.json(Map.of(
                "status", "healthy",
                "scraper", "active",
                "regions", Map.of(
                    "IDF", "monitoring",
                    "PARIS", "monitoring",
                    "VAL_MARNE", "monitoring",
                    "HAY_LES_ROSES", "monitoring"
                )
            ));
        });
        
        // Endpoint pour voir les stats (optionnel)
        app.get("/stats", ctx -> {
            ctx.json(Map.of(
                "message", "Scraper actif",
                "checkInterval", "10 secondes",
                "regions", 4
            ));
        });
        
        // Shutdown gracieux
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Arrêt du système...");
            scraper.stop();
            app.stop();
        }));
    }
}

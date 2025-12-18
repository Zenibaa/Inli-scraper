package com.inliscraper;

import io.javalin.Javalin;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Application principale du scraper Inli
 * Démarre le scraper et un serveur HTTP pour Railway
 */
public class App {
    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static void main(String[] args) {
        // Démarrer le scraper
        InliScraper scraper = new InliScraper();
        scraper.start();

        // Démarrer le serveur HTTP
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Javalin app = createServer(port);

        // Gestion de l'arrêt propre
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Arrêt du système...");
            scraper.stop();
            app.stop();
            System.out.println("✅ Système arrêté proprement");
        }));
    }

    private static Javalin createServer(int port) {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        System.out.println("🌐 Serveur HTTP démarré sur le port " + port);

        // Endpoint racine
        app.get("/", ctx -> {
            ctx.json(Map.of(
                    "status", "running",
                    "service", "Inli Scraper",
                    "timestamp", getCurrentDateTime(),
                    "timezone", "Europe/Paris"
            ));
        });

        // Endpoint de santé
        app.get("/health", ctx -> {
            ctx.json(Map.of(
                    "status", "healthy",
                    "scraper", "active",
                    "timestamp", getCurrentDateTime(),
                    "regions", Map.of(
                            "IDF", "monitoring",
                            "PARIS", "monitoring",
                            "VAL_MARNE", "monitoring",
                            "HAY_LES_ROSES", "monitoring"
                    )
            ));
        });

        // Endpoint de statistiques
        app.get("/stats", ctx -> {
            ctx.json(Map.of(
                    "message", "Scraper actif",
                    "checkInterval", "10 secondes",
                    "operatingHours", "06:30 - 20:30",
                    "regions", 4,
                    "notifications", "Une par offre",
                    "timezone", "Europe/Paris",
                    "currentTime", getCurrentDateTime()
            ));
        });

        return app;
    }

    private static String getCurrentDateTime() {
        return ZonedDateTime.now(PARIS_ZONE).format(DATE_TIME_FORMATTER);
    }
}

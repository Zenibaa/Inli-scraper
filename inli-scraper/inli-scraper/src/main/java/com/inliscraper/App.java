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
        try {
            // Démarrer le scraper
            InliScraper scraper = new InliScraper();
            scraper.start();
            
            // Démarrer le serveur HTTP
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            Javalin app = createServer(port);
            
            System.out.println("✅ Système démarré - En attente de pings externes pour rester actif");
            
            // Gestion de l'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 Arrêt du système...");
                scraper.stop();
                app.stop();
                System.out.println("✅ Système arrêté proprement");
            }));
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du démarrage : " + e.getMessage());
            System.err.println("💡 Si le port est déjà utilisé, essayez : PORT=8081 java -jar votre-app.jar");
            e.printStackTrace();
            System.exit(1);
        }
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
                "timezone", "Europe/Paris",
                "keepAlive", "external ping"
            ));
        });
        
        // Endpoint de santé
        app.get("/health", ctx -> {
            ctx.json(Map.of(
                "status", "healthy",
                "scraper", "active",
                "timestamp", getCurrentDateTime(),
                "regions", Map.of(
                    "PARIS", "monitoring",
                    "VAL_MARNE", "monitoring",
                    "HAUTS_DE_SEINE", "monitoring",
                    "ESSONNE", "monitoring",
                    "SEINE_SAINT_DENIS", "monitoring",
                    "SEINE_ET_MARNE", "monitoring",
                    "VAL_D_OISE", "monitoring",
                    "YVELINES", "monitoring"
                ),
                "keepAlive", "external ping"
            ));
        });
        
        // Endpoint de statistiques
        app.get("/stats", ctx -> {
            ctx.json(Map.of(
                "message", "Scraper actif",
                "checkInterval", "10 secondes",
                "operatingHours", "06:30 - 20:30",
                "regions", 8,
                "notifications", "Une par offre",
                "timezone", "Europe/Paris",
                "currentTime", getCurrentDateTime(),
                "keepAlive", "external ping (UptimeRobot ou similaire)"
            ));
        });
        
        // Endpoint pour ping externe - UTILISEZ CELUI-CI avec UptimeRobot
        app.get("/ping", ctx -> {
            String timestamp = getCurrentDateTime();
            System.out.println("════════════════════════════════════════");
            System.out.println("🏓 PING EXTERNE REÇU");
            System.out.println("📅 " + timestamp);
            System.out.println("🌍 IP: " + ctx.ip());
            System.out.println("════════════════════════════════════════");
            
            ctx.json(Map.of(
                "status", "pong",
                "timestamp", timestamp,
                "message", "Serveur actif et fonctionnel",
                "ip", ctx.ip()
            ));
        });
        
        return app;
    }
    
    private static String getCurrentDateTime() {
        return ZonedDateTime.now(PARIS_ZONE).format(DATE_TIME_FORMATTER);
    }
}

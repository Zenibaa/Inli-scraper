package com.inliscraper;

import io.javalin.Javalin;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static volatile boolean keepAliveRunning = true;
    
    public static void main(String[] args) {
        try {
            // Démarrer le scraper
            InliScraper scraper = new InliScraper();
            scraper.start();
            
            // Démarrer le serveur HTTP
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            Javalin app = createServer(port);
            
            // Keep-alive avec auto-ping pour éviter le sleeping
            startKeepAlive(port);
            
            // Gestion de l'arrêt propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 Arrêt du système...");
                keepAliveRunning = false;
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
    
    private static void startKeepAlive(int port) {
        Thread keepAliveThread = new Thread(() -> {
            System.out.println("💓 Thread Keep-Alive démarré");
            
            while (keepAliveRunning) {
                try {
                    // Afficher un log toutes les minutes
                    System.out.println("💓 KeepAlive - " + getCurrentDateTime());
                    
                    // Faire un auto-ping toutes les 5 minutes pour garder Railway actif
                    // Railway détecte l'activité HTTP et ne met pas en sleeping
                    Thread.sleep(5 * 60 * 1000); // 5 minutes
                    
                    // Auto-ping sur l'endpoint /health
                    pingHealthEndpoint(port);
                    
                } catch (InterruptedException e) {
                    System.out.println("⚠️ Thread Keep-Alive interrompu");
                    break;
                } catch (Exception e) {
                    System.err.println("⚠️ Erreur Keep-Alive: " + e.getMessage());
                }
            }
            
            System.out.println("💤 Thread Keep-Alive arrêté");
        });
        
        keepAliveThread.setDaemon(false); // Thread non-daemon pour garder l'app vivante
        keepAliveThread.start();
    }
    
    private static void pingHealthEndpoint(int port) {
        try {
            // Utiliser localhost car on est dans le même conteneur
            // Ça fonctionne aussi bien sur Railway qu'en local
            String urlString = "http://127.0.0.1:" + port + "/health";
            
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                System.out.println("🏓 Auto-ping réussi (HTTP " + responseCode + ")");
            } else {
                System.out.println("⚠️ Auto-ping réponse inhabituelle (HTTP " + responseCode + ")");
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            System.err.println("⚠️ Erreur auto-ping: " + e.getMessage());
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
                "keepAlive", "active"
            ));
        });
        
        // Endpoint de santé avec compteur de requêtes
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
                "keepAlive", "active"
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
                "keepAlive", "active - ping toutes les 5 minutes"
            ));
        });
        
        // Endpoint pour forcer le keep-alive manuel
        app.get("/ping", ctx -> {
            ctx.json(Map.of(
                "status", "pong",
                "timestamp", getCurrentDateTime(),
                "message", "Keep-Alive actif"
            ));
        });
        
        return app;
    }
    
    private static String getCurrentDateTime() {
        return ZonedDateTime.now(PARIS_ZONE).format(DATE_TIME_FORMATTER);
    }
}

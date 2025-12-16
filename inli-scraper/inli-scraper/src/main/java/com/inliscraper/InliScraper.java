package com.inliscraper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class InliScraper {

    // Configuration Inli
    private static final String DISCORD_WEBHOOK_URL_IDF = "https://discord.com/api/webhooks/1400482849962066053/Fsiw6MS6r37YFqiD5OX2lO_QgeSsqJorEwAeI2KasB73Mg42jdac35boDLzDnnoWi_WH";
    private static final String DISCORD_WEBHOOK_URL_PARIS = "https://discord.com/api/webhooks/1400825039129018439/rAuKWzhvwC30h3GIovT9l7yqxG9j5xXN6GsfklqAOC_4ymHbtXvj134S6sUDEF1Rwhdt";
    private static final String DISCORD_WEBHOOK_URL_VAL_MARNE = "https://discord.com/api/webhooks/1400832333774454926/xPjI6KPZk-PzGky11JdYH3m4ke-AdYkqlGRaJP5IpArIfz9asarKbwSN2sC5BTE7HXVy";
    private static final String DISCORD_WEBHOOK_URL_HAY_LES_ROSES = "https://discord.com/api/webhooks/1400835161880399872/z6YfCfCN5i3bpOARx5K6mJ2aYvxoTqbTuBw7EB_pgr2qmNCT-3aEC7jLWiWNI5D4vZOg";

    private static final String INLI_URL_IDF = "https://www.inli.fr/locations/offres/ile-_ile-?price_min=&price_max=957&area_min=37&area_max=81&room_min=1&room_max=2&bedroom_min=1&bedroom_max=5";
    private static final String INLI_URL_VAL_DE_MARNE = "https://www.inli.fr/locations/offres/Val-de-Marne%20(D%C3%A9partement)*_Val-de-Marne%20(D%C3%A9partement)*?price_min=&price_max=1000&area_min=35&area_max=";
    private static final String INLI_URL_PARIS = "https://www.inli.fr/locations/offres/paris-departement_d:75?price_min=&price_max=1000&area_min=30&area_max=&room_min=1&room_max=2&bedroom_min=1&bedroom_max=5";
    private static final String INLI_URL_HAY_LES_ROSES = "https://www.inli.fr/locations/offres/lhay-les-roses-94240_v:94240?price_min=&price_max=1000&area_min=35&area_max=";

    private static final int CHECK_INTERVAL_SECONDS = 10;

    // Headers pour simuler un navigateur réel
    private static final Map<String, String> HEADERS = createHeaders();

    private static Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        return headers;
    }

    private Map<String, Set<PropertyOffer>> previousOffersMap = new HashMap<>();
    private ObjectMapper objectMapper;
    private ScheduledExecutorService scheduler;

    public InliScraper() {
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initialiser les ensembles pour chaque région
        previousOffersMap.put("IDF", new HashSet<>());
        previousOffersMap.put("PARIS", new HashSet<>());
        previousOffersMap.put("VAL_MARNE", new HashSet<>());
        previousOffersMap.put("HAY_LES_ROSES", new HashSet<>());
    }

    public static void main(String[] args) {
        InliScraper scraper = new InliScraper();
        scraper.start();

        Runtime.getRuntime().addShutdownHook(new Thread(scraper::stop));
    }

    public void start() {
        System.out.println("🏠 Démarrage du scraper Inli...");
        System.out.println("📍 URLs:");
        System.out.println("  - IDF: " + INLI_URL_IDF);
        System.out.println("  - Paris: " + INLI_URL_PARIS);
        System.out.println("  - Val-de-Marne: " + INLI_URL_VAL_DE_MARNE);
        System.out.println("  - Hay-les-roses: " + INLI_URL_HAY_LES_ROSES);
        System.out.println("⏰ Vérification toutes les " + CHECK_INTERVAL_SECONDS + " secondes");

        checkForUpdates();

        scheduler.scheduleAtFixedRate(this::checkForUpdates,
                CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        System.out.println("🛑 Arrêt du scraper Inli...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

//    private void checkForUpdates() {
//        checkRegion("IDF", INLI_URL_IDF, DISCORD_WEBHOOK_URL_IDF);
//        checkRegion("PARIS", INLI_URL_PARIS, DISCORD_WEBHOOK_URL_PARIS);
//        checkRegion("VAL_MARNE", INLI_URL_VAL_DE_MARNE, DISCORD_WEBHOOK_URL_VAL_MARNE);
//    }
private void checkForUpdates() {
    // Crée un pool de 3 threads (un par région)
    ExecutorService executor = Executors.newFixedThreadPool(4);

    // Soumet les tâches pour chaque région
    executor.submit(() -> checkRegion("IDF", INLI_URL_IDF, DISCORD_WEBHOOK_URL_IDF));
    executor.submit(() -> checkRegion("PARIS", INLI_URL_PARIS, DISCORD_WEBHOOK_URL_PARIS));
    executor.submit(() -> checkRegion("VAL_MARNE", INLI_URL_VAL_DE_MARNE, DISCORD_WEBHOOK_URL_VAL_MARNE));
    executor.submit(() -> checkRegion("HAY_LES_ROSES", INLI_URL_HAY_LES_ROSES, DISCORD_WEBHOOK_URL_HAY_LES_ROSES));

    // Arrête proprement l'executor une fois les tâches terminées
    executor.shutdown();
}


    private void checkRegion(String regionName, String url, String webhookUrl) {
        try {
            System.out.println("🔍 Vérification Inli " + regionName + "... " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            Set<PropertyOffer> currentOffers = scrapeInliOffers(url);

            if (currentOffers.isEmpty()) {
                System.out.println("⚠️ Aucune annonce trouvée pour " + regionName);
                return;
            }

            Set<PropertyOffer> previousOffers = previousOffersMap.get(regionName);

            if (previousOffers.isEmpty()) {
                previousOffersMap.put(regionName, new HashSet<>(currentOffers));
                System.out.println("📊 " + currentOffers.size() + " annonces trouvées pour " + regionName + " (première exécution)");

                // Envoyer une notification de démarrage avec quelques exemples
                Set<PropertyOffer> examples = new HashSet<>();
                int count = 0;
                for (PropertyOffer offer : currentOffers) {
                    if (count >= 3) break;
                    examples.add(offer);
                    count++;
                }
                sendDiscordNotification(webhookUrl, "🚀 SCRAPER INLI DÉMARRÉ - " + regionName, examples, "bleu");
                return;
            }

            // Détecter les changements
            Set<PropertyOffer> newOffers = new HashSet<>(currentOffers);
            newOffers.removeAll(previousOffers);

            Set<PropertyOffer> removedOffers = new HashSet<>(previousOffers);
            removedOffers.removeAll(currentOffers);

            Set<PropertyOffer> modifiedOffers = new HashSet<>();
            for (PropertyOffer current : currentOffers) {
                for (PropertyOffer previous : previousOffers) {
                    if (current.getId().equals(previous.getId()) && !current.equals(previous)) {
                        modifiedOffers.add(current);
                        break;
                    }
                }
            }

            // Notifications Discord
            if (!newOffers.isEmpty()) {
                sendDiscordNotification(webhookUrl, "🆕 NOUVEAUX LOGEMENTS INLI - " + regionName, newOffers, "vert");
                System.out.println("✅ " + newOffers.size() + " nouveaux logements détectés pour " + regionName);
            }

            if (!modifiedOffers.isEmpty()) {
                sendDiscordNotification(webhookUrl, "🔄 LOGEMENTS MODIFIÉS - " + regionName, modifiedOffers, "orange");
                System.out.println("🔄 " + modifiedOffers.size() + " logements modifiés pour " + regionName);
            }

            if (!removedOffers.isEmpty()) {
                sendDiscordNotification(webhookUrl, "🗑️ LOGEMENTS SUPPRIMÉS - " + regionName, removedOffers, "rouge");
                System.out.println("🗑️ " + removedOffers.size() + " logements supprimés pour " + regionName);
            }

            previousOffersMap.put(regionName, new HashSet<>(currentOffers));

            if (newOffers.isEmpty() && modifiedOffers.isEmpty() && removedOffers.isEmpty()) {
                System.out.println("✅ Aucun changement pour " + regionName + " - " + currentOffers.size() + " annonces");
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la vérification pour " + regionName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Set<PropertyOffer> scrapeInliOffers(String url) throws IOException {
        Set<PropertyOffer> offers = new HashSet<>();

        try {
            // Récupérer la page HTML
            Connection connection = Jsoup.connect(url)
                    .timeout(0)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            // Ajouter tous les headers
            for (Map.Entry<String, String> header : HEADERS.entrySet()) {
                connection.header(header.getKey(), header.getValue());
            }

            // Simuler une session de navigation
            connection.cookie("sessionid", "fake-session-" + System.currentTimeMillis());

            Document doc = connection.get();

            System.out.println("📄 Status de la réponse: " + connection.response().statusCode());
            System.out.println("📄 Taille du contenu: " + doc.html().length() + " caractères");

            // Parser le HTML pour extraire les annonces
            offers = parseHtmlForOffers(doc);
            System.out.println("📊 " + offers.size() + " logements extraits du HTML");

        } catch (IOException e) {
            System.err.println("❌ Erreur de connexion: " + e.getMessage());
            throw e;
        }

        return offers;
    }

    private Set<PropertyOffer> parseHtmlForOffers(Document doc) {
        Set<PropertyOffer> offers = new HashSet<>();

        try {
            // Sélectionner tous les éléments avec la classe "featured-item"
            Elements propertyElements = doc.select(".featured-item");

            System.out.println("🔍 " + propertyElements.size() + " éléments .featured-item trouvés");

            for (Element propertyElement : propertyElements) {
                PropertyOffer offer = parsePropertyFromHtml(propertyElement);
                if (offer != null) {
                    offers.add(offer);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur lors du parsing HTML: " + e.getMessage());
            e.printStackTrace();
        }

        return offers;
    }

    private PropertyOffer parsePropertyFromHtml(Element propertyElement) {
        try {
            PropertyOffer offer = new PropertyOffer();

            // Extraire le lien principal
            Element linkElement = propertyElement.select("a").first();
            if (linkElement != null) {
                String relativeUrl = linkElement.attr("href");
                if (relativeUrl.startsWith("/")) {
                    offer.setUrl("https://www.inli.fr" + relativeUrl);
                } else {
                    offer.setUrl(relativeUrl);
                }

                // Extraire l'ID depuis l'URL (par exemple PRV-342145)
                String[] urlParts = relativeUrl.split("/");
                if (urlParts.length > 0) {
                    String lastPart = urlParts[urlParts.length - 1];
                    offer.setId(lastPart);
                }
            }

            // Extraire le prix
            Element priceElement = propertyElement.select(".featured-price .demi-condensed").first();
            if (priceElement != null) {
                String priceText = priceElement.text().trim();
                offer.setPrice(priceText);
            }

            // Extraire les détails (surface, nombre de pièces, ville)
            Element detailsElement = propertyElement.select(".featured-details span").first();
            if (detailsElement != null) {
                String detailsText = detailsElement.text();

                // Parser les détails : "Appartement · 2 pièces · 45.2 m² Montigny le bretonneux,"
                String[] parts = detailsText.split("·");

                if (parts.length >= 3) {
                    // Type de bien (Appartement)
                    String propertyType = parts[0].trim();

                    // Nombre de pièces
                    String roomsPart = parts[1].trim();
                    if (roomsPart.contains("pièce")) {
                        String rooms = roomsPart.replaceAll("[^0-9]", "");
                        offer.setRooms(rooms + " pièces");
                    }

                    // Surface et ville
                    String surfaceAndCity = parts[2].trim();
                    String[] surfaceCityParts = surfaceAndCity.split(" ");

                    // Trouver la surface (format: "45.2 m²")
                    for (int i = 0; i < surfaceCityParts.length - 1; i++) {
                        if (surfaceCityParts[i + 1].equals("m²")) {
                            offer.setArea(surfaceCityParts[i] + " m²");
                            break;
                        }
                    }

                    // La ville est généralement après la surface
                    StringBuilder cityBuilder = new StringBuilder();
                    boolean foundM2 = false;
                    for (String part : surfaceCityParts) {
                        if (foundM2 && !part.isEmpty() && !part.equals(",")) {
                            if (cityBuilder.length() > 0) {
                                cityBuilder.append(" ");
                            }
                            cityBuilder.append(part.replace(",", "").trim());
                        }
                        if (part.equals("m²")) {
                            foundM2 = true;
                        }
                    }
                    offer.setLocation(cityBuilder.toString());

                    // Titre combiné
                    offer.setTitle(propertyType + " - " + offer.getRooms() + " - " + offer.getArea() + " - " + offer.getLocation());
                }
            }

            // Extraire la description détaillée
            Element extraDetailsElement = propertyElement.select(".featured-details-extra").first();
            if (extraDetailsElement != null) {
                String description = extraDetailsElement.text().trim();
                // Nettoyer la description (limiter la longueur pour Discord)
                if (description.length() > 200) {
                    description = description.substring(0, 200) + "...";
                }
                offer.setDescription(description);
            }

            offer.setLastUpdated(LocalDateTime.now());

            // Validation : vérifier que l'offre a des informations minimales
            if (offer.getId() != null && !offer.getId().isEmpty() &&
                    offer.getPrice() != null && !offer.getPrice().isEmpty()) {

                System.out.println("✅ Logement extrait: " + offer.getId() + " - " +
                        offer.getPrice() + " - " + offer.getArea() + " - " + offer.getLocation());
                return offer;
            } else {
                System.out.println("⚠️ Logement ignoré (données manquantes): ID=" + offer.getId() +
                        ", Prix=" + offer.getPrice());
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erreur extraction HTML: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private void sendDiscordNotification(String webhookUrl, String title, Set<PropertyOffer> offers, String color) {
        try {
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", title);
            embed.put("description", formatOffersForDiscord(offers));
            embed.put("color", getColorCode(color));
            embed.put("timestamp", LocalDateTime.now().toString());

            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Inli Scraper Bot");
            embed.put("footer", footer);
            String regionUrl;
            if (title.contains("PARIS")) {
                regionUrl = INLI_URL_PARIS;
            } else if (title.contains("VAL_MARNE")) {
                regionUrl = INLI_URL_VAL_DE_MARNE;
            } else {
                regionUrl = INLI_URL_IDF;
            }
            embed.put("url", regionUrl);

            Map<String, Object> payload = new HashMap<>();
            List<Object> embeds = new ArrayList<>();
            embeds.add(embed);
            payload.put("embeds", embeds);

            String json = objectMapper.writeValueAsString(payload);

            sendHttpPost(webhookUrl, json);

            System.out.println("📢 Notification Discord envoyée: " + title +
                    " (" + offers.size() + " logement(s))");

        } catch (Exception e) {
            System.err.println("❌ Erreur notification Discord: " + e.getMessage());
        }
    }

    private void sendHttpPost(String url, String jsonPayload) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();

        // Configuration de la requête
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", HEADERS.get("User-Agent"));
        connection.setDoOutput(true);

        // Envoyer les données
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Lire la réponse
        int responseCode = connection.getResponseCode();
        if (responseCode != 204 && responseCode != 200) {
            System.err.println("❌ Erreur Discord: " + responseCode);

            // Lire le message d'erreur
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.err.println("Détails erreur: " + response.toString());
            }
        }
    }

    private String formatOffersForDiscord(Set<PropertyOffer> offers) {
        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (PropertyOffer offer : offers) {
            if (count >= 20) { // Limiter à 10 annonces par notification Discord
                sb.append("... et ").append(offers.size() - count).append(" autres logements");
                break;
            }

            String title = offer.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Logement ID: " + offer.getId();
            }
            sb.append("🏠 **").append(title).append("**\n");

            // if (offer.getPrice() != null && !offer.getPrice().isEmpty()) {
            //     sb.append("💰 ").append(offer.getPrice()).append("\n");
            // }
            // if (offer.getArea() != null && !offer.getArea().isEmpty()) {
            //     sb.append("📏 ").append(offer.getArea()).append("\n");
            // }
            // if (offer.getRooms() != null && !offer.getRooms().isEmpty()) {
            //     sb.append("🚪 ").append(offer.getRooms()).append("\n");
            // }
            // if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
            //     sb.append("📍 ").append(offer.getLocation()).append("\n");
            // }
            // if (offer.getDescription() != null && !offer.getDescription().isEmpty()) {
            //     sb.append("📄 ").append(offer.getDescription()).append("\n");
            // }
            if (offer.getUrl() != null && !offer.getUrl().isEmpty()) {
                sb.append("🔗 [Voir l'annonce](").append(offer.getUrl()).append(")\n");
            }
            sb.append("\n");
            count++;
        }

        return sb.toString();
    }

    private int getColorCode(String color) {
        switch (color.toLowerCase()) {
            case "vert": return 0x00ff00;
            case "orange": return 0xffa500;
            case "rouge": return 0xff0000;
            case "bleu": return 0x0099ff;
            default: return 0x0099ff;
        }
    }

}

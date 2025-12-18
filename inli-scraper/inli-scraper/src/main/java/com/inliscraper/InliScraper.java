package com.inliscraper;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Scraper pour les offres immobilières Inli
 * Surveille plusieurs régions et envoie des notifications Discord
 */
public class InliScraper {

    // Configuration des webhooks Discord
    private static final String DISCORD_WEBHOOK_URL_IDF = "https://discord.com/api/webhooks/1400482849962066053/Fsiw6MS6r37YFqiD5OX2lO_QgeSsqJorEwAeI2KasB73Mg42jdac35boDLzDnnoWi_WH";
    private static final String DISCORD_WEBHOOK_URL_PARIS = "https://discord.com/api/webhooks/1400825039129018439/rAuKWzhvwC30h3GIovT9l7yqxG9j5xXN6GsfklqAOC_4ymHbtXvj134S6sUDEF1Rwhdt";
    private static final String DISCORD_WEBHOOK_URL_VAL_MARNE = "https://discord.com/api/webhooks/1400832333774454926/xPjI6KPZk-PzGky11JdYH3m4ke-AdYkqlGRaJP5IpArIfz9asarKbwSN2sC5BTE7HXVy";
    private static final String DISCORD_WEBHOOK_URL_HAY_LES_ROSES = "https://discord.com/api/webhooks/1400835161880399872/z6YfCfCN5i3bpOARx5K6mJ2aYvxoTqbTuBw7EB_pgr2qmNCT-3aEC7jLWiWNI5D4vZOg";

    // URLs de recherche Inli
    private static final String INLI_URL_IDF = "https://www.inli.fr/locations/offres/ile-de-france-region_r:11?price_min=0&price_max=1015&area_min=0&area_max=250&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_VAL_DE_MARNE = "https://www.inli.fr/locations/offres/Val-de-Marne%20(D%C3%A9partement)*_Val-de-Marne%20(D%C3%A9partement)*?price_min=&price_max=1000&area_min=35&area_max=";
    private static final String INLI_URL_PARIS = "https://www.inli.fr/locations/offres/paris-departement_d:75?price_min=0&price_max=1500&area_min=&area_max=&room_min=1&room_max=5&bedroom_min=1&bedroom_max=5";
    private static final String INLI_URL_HAY_LES_ROSES = "https://www.inli.fr/locations/offres/lhay-les-roses-94240_v:94240?price_min=&price_max=1000&area_min=35&area_max=";

    private static final int CHECK_INTERVAL_SECONDS = 10;
    private static final int THREAD_POOL_SIZE = 4;
    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Headers pour simuler un navigateur
    private static final Map<String, String> HEADERS = createHeaders();

    private final Map<String, Set<PropertyOffer>> previousOffersMap = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    public InliScraper() {
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initialiser les ensembles pour chaque région
        previousOffersMap.put("IDF", new HashSet<>());
        previousOffersMap.put("PARIS", new HashSet<>());
        previousOffersMap.put("VAL_MARNE", new HashSet<>());
        previousOffersMap.put("HAY_LES_ROSES", new HashSet<>());
    }

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

    public void start() {
        System.out.println("🏠 Démarrage du scraper Inli...");
        System.out.println("📍 URLs surveillées :");
        System.out.println("  - IDF: " + INLI_URL_IDF);
        System.out.println("  - Paris: " + INLI_URL_PARIS);
        System.out.println("  - Val-de-Marne: " + INLI_URL_VAL_DE_MARNE);
        System.out.println("  - L'Haÿ-les-Roses: " + INLI_URL_HAY_LES_ROSES);
        System.out.println("⏰ Vérification toutes les " + CHECK_INTERVAL_SECONDS + " secondes");
        System.out.println("🕐 Heure locale : " + getCurrentTime());

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
            Thread.currentThread().interrupt();
        }
    }

    private void checkForUpdates() {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        executor.submit(() -> checkRegion("IDF", INLI_URL_IDF, DISCORD_WEBHOOK_URL_IDF));
        executor.submit(() -> checkRegion("PARIS", INLI_URL_PARIS, DISCORD_WEBHOOK_URL_PARIS));
        executor.submit(() -> checkRegion("VAL_MARNE", INLI_URL_VAL_DE_MARNE, DISCORD_WEBHOOK_URL_VAL_MARNE));
        executor.submit(() -> checkRegion("HAY_LES_ROSES", INLI_URL_HAY_LES_ROSES, DISCORD_WEBHOOK_URL_HAY_LES_ROSES));

        executor.shutdown();
    }

    private void checkRegion(String regionName, String url, String webhookUrl) {
        try {
            System.out.println("🔍 Vérification " + regionName + " à " + getCurrentTime());

            Set<PropertyOffer> currentOffers = scrapeInliOffers(url);

            if (currentOffers.isEmpty()) {
                System.out.println("⚠️ Aucune annonce trouvée pour " + regionName);
                return;
            }

            Set<PropertyOffer> previousOffers = previousOffersMap.get(regionName);

            if (previousOffers.isEmpty()) {
                previousOffersMap.put(regionName, new HashSet<>(currentOffers));
                System.out.println("📊 " + currentOffers.size() + " annonces initiales pour " + regionName);
                sendStartupNotification(webhookUrl, regionName, currentOffers.size());
                return;
            }

            // Détecter les changements
            Set<PropertyOffer> newOffers = findNewOffers(currentOffers, previousOffers);
            Set<PropertyOffer> removedOffers = findRemovedOffers(currentOffers, previousOffers);
            Set<PropertyOffer> modifiedOffers = findModifiedOffers(currentOffers, previousOffers);

            // Envoyer une notification par offre
            processOffers(webhookUrl, regionName, newOffers, "NOUVEAU", "vert");
            processOffers(webhookUrl, regionName, modifiedOffers, "MODIFIÉ", "orange");
            processOffers(webhookUrl, regionName, removedOffers, "SUPPRIMÉ", "rouge");

            previousOffersMap.put(regionName, new HashSet<>(currentOffers));

            if (newOffers.isEmpty() && modifiedOffers.isEmpty() && removedOffers.isEmpty()) {
                System.out.println("✅ Aucun changement pour " + regionName + " (" + currentOffers.size() + " annonces)");
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur pour " + regionName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Set<PropertyOffer> findNewOffers(Set<PropertyOffer> current, Set<PropertyOffer> previous) {
        Set<PropertyOffer> newOffers = new HashSet<>(current);
        newOffers.removeAll(previous);
        return newOffers;
    }

    private Set<PropertyOffer> findRemovedOffers(Set<PropertyOffer> current, Set<PropertyOffer> previous) {
        Set<PropertyOffer> removed = new HashSet<>(previous);
        removed.removeAll(current);
        return removed;
    }

    private Set<PropertyOffer> findModifiedOffers(Set<PropertyOffer> current, Set<PropertyOffer> previous) {
        Set<PropertyOffer> modified = new HashSet<>();
        for (PropertyOffer currentOffer : current) {
            for (PropertyOffer previousOffer : previous) {
                if (currentOffer.getId().equals(previousOffer.getId()) &&
                        !currentOffer.equals(previousOffer)) {
                    modified.add(currentOffer);
                    break;
                }
            }
        }
        return modified;
    }

    private void processOffers(String webhookUrl, String regionName,
                               Set<PropertyOffer> offers, String status, String color) {
        if (offers.isEmpty()) return;

        System.out.println("📢 " + offers.size() + " logement(s) " + status.toLowerCase() + "(s) pour " + regionName);

        for (PropertyOffer offer : offers) {
            sendSingleOfferNotification(webhookUrl, regionName, offer, status, color);

            // Petit délai entre les notifications pour éviter le rate limiting
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendSingleOfferNotification(String webhookUrl, String regionName,
                                             PropertyOffer offer, String status, String color) {
        try {
            Map<String, Object> embed = new HashMap<>();

            // Le titre avec lien cliquable
            String offerTitle = offer.getTitle().isEmpty() ? "Logement " + offer.getId() : offer.getTitle();
            embed.put("title", "🏠 " + status + " - " + regionName + " - " + offerTitle);
            embed.put("url", offer.getUrl());

            embed.put("description", formatOfferForDiscord(offer));
            embed.put("color", getColorCode(color));
            embed.put("timestamp", ZonedDateTime.now(PARIS_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Inli Scraper • " + getCurrentTime());
            embed.put("footer", footer);

            Map<String, Object> payload = new HashMap<>();
            payload.put("embeds", Collections.singletonList(embed));

            String json = objectMapper.writeValueAsString(payload);
            sendHttpPost(webhookUrl, json);

        } catch (Exception e) {
            System.err.println("❌ Erreur notification Discord: " + e.getMessage());
        }
    }

    private void sendStartupNotification(String webhookUrl, String regionName, int count) {
        try {
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "🚀 Scraper démarré - " + regionName);
            embed.put("description", "Surveillance active\n" + count + " annonces détectées");
            embed.put("color", getColorCode("bleu"));
            embed.put("timestamp", ZonedDateTime.now(PARIS_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Inli Scraper • " + getCurrentTime());
            embed.put("footer", footer);

            Map<String, Object> payload = new HashMap<>();
            payload.put("embeds", Collections.singletonList(embed));

            String json = objectMapper.writeValueAsString(payload);
            sendHttpPost(webhookUrl, json);

        } catch (Exception e) {
            System.err.println("❌ Erreur notification de démarrage: " + e.getMessage());
        }
    }

    private String formatOfferForDiscord(PropertyOffer offer) {
        StringBuilder sb = new StringBuilder();

        if (!offer.getPrice().isEmpty()) {
            sb.append("💰 **Prix:** ").append(offer.getPrice()).append("\n");
        }
        if (!offer.getArea().isEmpty()) {
            sb.append("📏 **Surface:** ").append(offer.getArea()).append("\n");
        }
        if (!offer.getRooms().isEmpty()) {
            sb.append("🚪 **Pièces:** ").append(offer.getRooms()).append("\n");
        }
        if (!offer.getLocation().isEmpty()) {
            sb.append("📍 **Localisation:** ").append(offer.getLocation()).append("\n");
        }

        sb.append("\n[🔗 Cliquez ici pour voir l'annonce](").append(offer.getUrl()).append(")");

        return sb.toString();
    }

    private Set<PropertyOffer> scrapeInliOffers(String url) throws IOException {
        Set<PropertyOffer> offers = new HashSet<>();

        try {
            Connection connection = Jsoup.connect(url)
                    .timeout(30000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);

            HEADERS.forEach(connection::header);
            connection.cookie("sessionid", "fake-session-" + System.currentTimeMillis());

            Document doc = connection.get();

            System.out.println("📄 Réponse HTTP " + connection.response().statusCode() +
                    " - " + doc.html().length() + " caractères");

            offers = parseHtmlForOffers(doc);
            System.out.println("📊 " + offers.size() + " offres extraites");

        } catch (IOException e) {
            System.err.println("❌ Erreur de connexion: " + e.getMessage());
            throw e;
        }

        return offers;
    }

    private Set<PropertyOffer> parseHtmlForOffers(Document doc) {
        Set<PropertyOffer> offers = new HashSet<>();

        try {
            Elements propertyElements = doc.select(".featured-item");
            System.out.println("🔍 " + propertyElements.size() + " éléments .featured-item trouvés");

            for (Element element : propertyElements) {
                PropertyOffer offer = parsePropertyFromHtml(element);
                if (offer != null) {
                    offers.add(offer);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erreur parsing HTML: " + e.getMessage());
            e.printStackTrace();
        }

        return offers;
    }

    private PropertyOffer parsePropertyFromHtml(Element element) {
        try {
            PropertyOffer offer = new PropertyOffer();

            // Extraire l'URL et l'ID
            Element linkElement = element.select("a").first();
            if (linkElement != null) {
                String relativeUrl = linkElement.attr("href");
                offer.setUrl(relativeUrl.startsWith("/") ? "https://www.inli.fr" + relativeUrl : relativeUrl);

                String[] urlParts = relativeUrl.split("/");
                if (urlParts.length > 0) {
                    offer.setId(urlParts[urlParts.length - 1]);
                }
            }

            // Extraire le prix
            Element priceElement = element.select(".featured-price .demi-condensed").first();
            if (priceElement != null) {
                offer.setPrice(priceElement.text().trim());
            }

            // Extraire les détails
            Element detailsElement = element.select(".featured-details span").first();
            if (detailsElement != null) {
                parseDetails(offer, detailsElement.text());
            }

            // Extraire la description
            Element extraDetailsElement = element.select(".featured-details-extra").first();
            if (extraDetailsElement != null) {
                String description = extraDetailsElement.text().trim();
                if (description.length() > 200) {
                    description = description.substring(0, 200) + "...";
                }
                offer.setDescription(description);
            }

            offer.updateTimestamp();

            // Validation
            if (offer.getId() != null && !offer.getId().isEmpty() &&
                    offer.getPrice() != null && !offer.getPrice().isEmpty()) {
                return offer;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erreur extraction: " + e.getMessage());
        }

        return null;
    }

    private void parseDetails(PropertyOffer offer, String detailsText) {
        String[] parts = detailsText.split("·");

        if (parts.length >= 3) {
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

            // Extraire la surface
            for (int i = 0; i < surfaceCityParts.length - 1; i++) {
                if (surfaceCityParts[i + 1].equals("m²")) {
                    offer.setArea(surfaceCityParts[i] + " m²");
                    break;
                }
            }

            // Extraire la ville
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

            // Construire le titre
            offer.setTitle(String.format("%s - %s - %s - %s",
                    propertyType, offer.getRooms(), offer.getArea(), offer.getLocation()));
        }
    }

    private void sendHttpPost(String url, String jsonPayload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", HEADERS.get("User-Agent"));
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 204 && responseCode != 200) {
            System.err.println("❌ Erreur Discord: " + responseCode);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                System.err.println("Détails: " + response.toString());
            }
        }
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

    private String getCurrentTime() {
        return ZonedDateTime.now(PARIS_ZONE).format(TIME_FORMATTER);
    }
}

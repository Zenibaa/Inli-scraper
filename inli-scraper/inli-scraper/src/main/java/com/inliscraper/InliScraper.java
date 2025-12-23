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
    private static final String DISCORD_WEBHOOK_URL_PARIS = "https://discord.com/api/webhooks/1400825039129018439/rAuKWzhvwC30h3GIovT9l7yqxG9j5xXN6GsfklqAOC_4ymHbtXvj134S6sUDEF1Rwhdt";
    private static final String DISCORD_WEBHOOK_URL_VAL_MARNE = "https://discord.com/api/webhooks/1400832333774454926/xPjI6KPZk-PzGky11JdYH3m4ke-AdYkqlGRaJP5IpArIfz9asarKbwSN2sC5BTE7HXVy";
    private static final String DISCORD_WEBHOOK_URL_HAUTS_DE_SEINE = "https://discord.com/api/webhooks/1451557533004071039/MwRGb2edhIKl3BT_nQa2WfRqQD4up5250-_M-1JTBLq0dm5w41g-_9oOsqRMHRo9duLR";
    private static final String DISCORD_WEBHOOK_URL_SEINE_ET_MARNE = "https://discord.com/api/webhooks/1451557813439692800/cMSgKI_Yp1MLGUtWnmBJLi56Ve7606oRKFI70Wl6h40BjdqXJR0i51mIUOfiaHSSRGmt";
    private static final String DISCORD_WEBHOOK_URL_SEINE_SAINT_DENIS = "https://discord.com/api/webhooks/1451557717230616611/ZiFN7wUC_8KwDzxgNsiRWP_BsTnhurVChzRZ7jrSk1VwJ3xDOaa0W5drcXL2Jez60d0x";
    private static final String DISCORD_WEBHOOK_URL_YVELINES = "https://discord.com/api/webhooks/1451558004594839694/vwD8KJpqAmm00JB8yGXUkp_1lDAQ1V2THAqET1l3wYFXtzO3Fm2HlmTM2XPlyFSG6OJ4";
    private static final String DISCORD_WEBHOOK_URL_ESSONNE = "https://discord.com/api/webhooks/1451557305786302617/CShiQ8Y5t2MHoarHL0oCnP9UGVxRsFwfgWNxw4Un8kBQXlcOL-xeqQiLatHrj60U6GHK";
    private static final String DISCORD_WEBHOOK_URL_VAL_D_OISE = "https://discord.com/api/webhooks/1451558218877763687/mn6C0DH_DIFfmdP2QHgRmr8bWdEKcqYDpxylC5kS_335S6utRMvlk52kTYMex_qIxfuv";

    // URLs de recherche Inli
    private static final String INLI_URL_VAL_DE_MARNE = "https://www.inli.fr/locations/offres/val-de-marne-departement_d:94?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_PARIS = "https://www.inli.fr/locations/offres/paris-departement_d:75?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_HAUTS_DE_SEINE = "https://www.inli.fr/locations/offres/hauts-de-seine-departement_d:92?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_SEINE_SAINT_DENIS = "https://www.inli.fr/locations/offres/seine-saint-denis-departement_d:93?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_SEINE_ET_MARNE = "https://www.inli.fr/locations/offres/seine-et-marne-departement_d:77?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_YVELINES = "https://www.inli.fr/locations/offres/yvelines-departement_d:78?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_ESSONNE = "https://www.inli.fr/locations/offres/essonne-departement_d:91?price_min=0&price_max=1100&area_min=0&area_max=250&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";
    private static final String INLI_URL_VAL_D_OISE = "https://www.inli.fr/locations/offres/val-doise-departement_d:95?price_min=&price_max=1100&area_min=&area_max=&room_min=0&room_max=5&bedroom_min=0&bedroom_max=5";

    private static final int CHECK_INTERVAL_SECONDS = 10;
    private static final int THREAD_POOL_SIZE = 2;
    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Horaires de surveillance
    private static final int START_HOUR = 6;
    private static final int START_MINUTE = 30;
    private static final int END_HOUR = 20;
    private static final int END_MINUTE = 30;

    // Headers pour simuler un navigateur
    private static final Map<String, String> HEADERS = createHeaders();

    private final Map<String, Set<PropertyOffer>> previousOffersMap = new HashMap<>();
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    public InliScraper() {
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Initialiser les ensembles pour chaque région
        previousOffersMap.put("PARIS", new HashSet<>());
        previousOffersMap.put("VAL_MARNE", new HashSet<>());
        previousOffersMap.put("HAUTS_DE_SEINE", new HashSet<>());
        previousOffersMap.put("ESSONNE", new HashSet<>());
        previousOffersMap.put("SEINE_SAINT_DENIS", new HashSet<>());
        previousOffersMap.put("SEINE_ET_MARNE", new HashSet<>());
        previousOffersMap.put("VAL_D_OISE", new HashSet<>());
        previousOffersMap.put("YVELINES", new HashSet<>());
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
        System.out.println("  - Paris: " + INLI_URL_PARIS);
        System.out.println("  - Val-de-Marne: " + INLI_URL_VAL_DE_MARNE);
        System.out.println("  - Hauts de seine: " + INLI_URL_HAUTS_DE_SEINE);
        System.out.println("  - Essonne: " + INLI_URL_ESSONNE);
        System.out.println("  - Seine et marne: " + INLI_URL_SEINE_ET_MARNE);
        System.out.println("  - Seine saint denis: " + INLI_URL_SEINE_SAINT_DENIS);
        System.out.println("  - Val d'oise: " + INLI_URL_VAL_D_OISE);
        System.out.println("  - Yvelines: " + INLI_URL_YVELINES);
        System.out.println("⏰ Vérification toutes les " + CHECK_INTERVAL_SECONDS + " secondes");
        System.out.println("🕐 Horaires de surveillance : " + START_HOUR + "h" + String.format("%02d", START_MINUTE) +
                " - " + END_HOUR + "h" + String.format("%02d", END_MINUTE));
        System.out.println("🕐 Heure locale : " + getCurrentTime());

        // Vérification initiale (le scheduler prendra le relais après)
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
        // Vérifier si on est dans les horaires de surveillance
        if (!isWithinOperatingHours()) {
            // Afficher un log seulement une fois par heure pour éviter le spam
            ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);
            if (now.getMinute() == 0 && now.getSecond() < CHECK_INTERVAL_SECONDS) {
                System.out.println("⏸️ Hors horaires de surveillance (" + getCurrentTime() +
                        ") - Reprise à " + String.format("%02d:%02d", START_HOUR, START_MINUTE));
            }
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        executor.submit(() -> checkRegion("PARIS", INLI_URL_PARIS, DISCORD_WEBHOOK_URL_PARIS));
        executor.submit(() -> checkRegion("VAL_MARNE", INLI_URL_VAL_DE_MARNE, DISCORD_WEBHOOK_URL_VAL_MARNE));
        executor.submit(() -> checkRegion("HAUTS_DE_SEINE", INLI_URL_HAUTS_DE_SEINE, DISCORD_WEBHOOK_URL_HAUTS_DE_SEINE));
        executor.submit(() -> checkRegion("ESSONNE", INLI_URL_ESSONNE, DISCORD_WEBHOOK_URL_ESSONNE));
        executor.submit(() -> checkRegion("SEINE_SAINT_DENIS", INLI_URL_SEINE_SAINT_DENIS, DISCORD_WEBHOOK_URL_SEINE_SAINT_DENIS));
        executor.submit(() -> checkRegion("SEINE_ET_MARNE", INLI_URL_SEINE_ET_MARNE, DISCORD_WEBHOOK_URL_SEINE_ET_MARNE));
        executor.submit(() -> checkRegion("VAL_D_OISE", INLI_URL_VAL_D_OISE, DISCORD_WEBHOOK_URL_VAL_D_OISE));
        executor.submit(() -> checkRegion("YVELINES", INLI_URL_YVELINES, DISCORD_WEBHOOK_URL_YVELINES));


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
            String offerTitle = offer.getTitle().isEmpty() ? "Logement " + offer.getId() :
                    "Appartement •" + offer.getRooms() + " •" + offer.getArea() + " •" + offer.getLocation()+ " •" + offer.getPrice();

            String prefix ="🏠 ";
            if(status.equals("NOUVEAU")){
                prefix = "🆕 ";
            } else if(status.equals("SUPPRIMÉ")){
                prefix = "🗑️ ";
            }
            embed.put("title", prefix + offerTitle);
            embed.put("url", offer.getUrl());

//          embed.put("description", formatOfferForDiscord(offer));
            embed.put("color", getColorCode(color));
            embed.put("timestamp", ZonedDateTime.now(PARIS_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Source : " + offer.getLocation());
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
            embed.put("title", "🚀 Scraper démarré | " + regionName);
            embed.put("description", "Surveillance active\n" + count + " annonces détectées");
            embed.put("color", getColorCode("bleu"));
            embed.put("timestamp", ZonedDateTime.now(PARIS_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Source : Railway");
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

    /**
     * Vérifie si on est dans les horaires de surveillance (6h30 - 20h30)
     */
    private boolean isWithinOperatingHours() {
        ZonedDateTime now = ZonedDateTime.now(PARIS_ZONE);
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Convertir en minutes pour faciliter la comparaison
        int currentTimeInMinutes = currentHour * 60 + currentMinute;
        int startTimeInMinutes = START_HOUR * 60 + START_MINUTE;
        int endTimeInMinutes = END_HOUR * 60 + END_MINUTE;

        return currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes <= endTimeInMinutes;
    }
}



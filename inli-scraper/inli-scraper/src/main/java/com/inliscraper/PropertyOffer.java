package com.inliscraper;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Représente une offre immobilière
 */
public class PropertyOffer {
    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

    private String id;
    private String title;
    private String price;
    private String area;
    private String rooms;
    private String location;
    private String description;
    private String url;
    private ZonedDateTime lastUpdated;

    public PropertyOffer() {
        this.lastUpdated = ZonedDateTime.now(PARIS_ZONE);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getPrice() {
        return price != null ? price : "";
    }

    public String getArea() {
        return area != null ? area : "";
    }

    public String getRooms() {
        return rooms != null ? rooms : "";
    }

    public String getLocation() {
        return location != null ? location : "";
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public String getUrl() {
        return url != null ? url : "";
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public void setRooms(String rooms) {
        this.rooms = rooms;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setLastUpdated(ZonedDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void updateTimestamp() {
        this.lastUpdated = ZonedDateTime.now(PARIS_ZONE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyOffer that = (PropertyOffer) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(price, that.price) &&
                Objects.equals(area, that.area) &&
                Objects.equals(rooms, that.rooms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, price, area, rooms);
    }

    @Override
    public String toString() {
        return String.format("PropertyOffer{id='%s', title='%s', price='%s', area='%s', rooms='%s', location='%s'}",
                id, title, price, area, rooms, location);
    }
}

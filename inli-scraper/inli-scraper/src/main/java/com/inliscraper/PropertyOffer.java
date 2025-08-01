package com.inliscraper;

import java.time.LocalDateTime;
import java.util.Objects;

// Classe pour les offres immobilières
public class PropertyOffer {
    private String id;
    private String title;
    private String price;
    private String area;
    private String rooms;
    private String location;
    private String description;
    private String url;
    private LocalDateTime lastUpdated;

    public PropertyOffer() {}

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getPrice() { return price != null ? price : ""; }
    public void setPrice(String price) { this.price = price; }

    public String getArea() { return area != null ? area : ""; }
    public void setArea(String area) { this.area = area; }

    public String getRooms() { return rooms != null ? rooms : ""; }
    public void setRooms(String rooms) { this.rooms = rooms; }

    public String getLocation() { return location != null ? location : ""; }
    public void setLocation(String location) { this.location = location; }

    public String getUrl() { return url != null ? url : ""; }
    public void setUrl(String url) { this.url = url; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "PropertyOffer{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", price='" + price + '\'' +
                ", area='" + area + '\'' +
                ", rooms='" + rooms + '\'' +
                ", location='" + location + '\'' +
                ", description='" + description + '\'' +
                ", url='" + url + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
package com.salonbooking.catalog.domain;

import com.salonbooking.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A branch of a business. */
@Entity
@Table(name = "location")
public class Location extends TenantOwnedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(nullable = false)
    private String suburb;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String postcode;

    private String phone;

    private String email;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(nullable = false)
    private boolean active;

    protected Location() {}

    public Location(String name, String slug, String addressLine1, String suburb, String state, String postcode,
            String timeZone) {
        this.name = name;
        this.slug = slug;
        this.addressLine1 = addressLine1;
        this.suburb = suburb;
        this.state = state;
        this.postcode = postcode;
        this.timeZone = timeZone;
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getSuburb() {
        return suburb;
    }

    public String getState() {
        return state;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public boolean isActive() {
        return active;
    }
}

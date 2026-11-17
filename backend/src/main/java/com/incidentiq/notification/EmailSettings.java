package com.incidentiq.notification;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether email is set up, who it's from and where its links point. Email is
 * on only when both an SMTP host and a sender address are configured.
 */
@Component
public class EmailSettings {

    private final String host;
    private final String from;
    private final String appUrl;

    public EmailSettings(
            @Value("${spring.mail.host:}") String host,
            @Value("${incidentiq.mail.from:}") String from,
            @Value("${incidentiq.mail.app-url:http://localhost:5173}") String appUrl) {

        this.host = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
        String url = appUrl == null ? "" : appUrl.trim();
        this.appUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public boolean enabled() {
        return !host.isEmpty() && !from.isEmpty();
    }

    public String from() {
        return from;
    }

    public String incidentLink(UUID incidentId) {
        return appUrl + "/incidents/" + incidentId;
    }

    public String forgotPasswordLink() {
        return appUrl + "/forgot-password";
    }

    public String accountLink() {
        return appUrl + "/account";
    }

    /** The token goes after "#", which browsers never send to a server. */
    public String emailChangeLink(String token) {
        return appUrl + "/confirm-email#token=" + token;
    }

    /** The token goes after "#", which browsers never send to a server. */
    public String passwordResetLink(String token) {
        return appUrl + "/reset-password#token=" + token;
    }
}

package com.salonbooking.notification.mail;

/** Hands an email to a delivery service. "Sent" means accepted, not delivered (NTF-6). */
public interface MailGateway {

    /** Returns the provider's message id. Throws if the email was not accepted. */
    String send(OutgoingEmail email) throws Exception;
}

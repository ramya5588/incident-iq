package com.incidentiq.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends notification emails. Runs only after the change that caused the
 * notification has been saved (so a rolled-back change sends nothing), and
 * on a background thread (so a slow mail server never slows a request). A
 * failed send is logged; the in-app notification is already saved.
 */
@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final EmailSettings settings;

    public EmailNotifier(ObjectProvider<JavaMailSender> mailSender, EmailSettings settings) {
        this.mailSender = mailSender;
        this.settings = settings;
    }

    @Async(MailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(NotificationEmail email) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || !settings.enabled()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(email.to());
        message.setSubject(email.message());
        message.setText(body(email));

        try {
            sender.send(message);
        } catch (MailException exception) {
            log.warn("Couldn't email a notification to user {}: {}",
                    email.recipientId(), exception.getMessage());
        }
    }

    String body(NotificationEmail email) {
        return """
                Hi %s,

                %s.

                Open the incident: %s

                You get this email because of your notification settings. \
                Change them on your Account page: %s
                """.formatted(
                email.recipientName(),
                email.message(),
                settings.incidentLink(email.incidentId()),
                settings.accountLink());
    }
}

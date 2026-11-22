package com.EOT.project.passwordreset;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the themed "reset your password" email. When BREVO_API_KEY is set it uses Brevo's HTTPS API
 * (works on hosts that block SMTP ports); otherwise it falls back to plain SMTP.
 */
@Component
public class PasswordResetMailer {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SUBJECT = "Echoes of Time · Restablece tu contraseña";
    private static final Pattern NAMED_ADDRESS = Pattern.compile("^\\s*\"?([^<\"]*?)\"?\\s*<([^>]+)>\\s*$");

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final String host;
    private final String brevoKey;
    private final RestClient http;

    @Autowired
    public PasswordResetMailer(ObjectProvider<JavaMailSender> sender,
                               @Value("${app.mail.from:no-reply@eot.local}") String from,
                               @Value("${spring.mail.host:}") String host,
                               @Value("${app.mail.brevo-api-key:}") String brevoKey) {
        this(sender, from, host, brevoKey, newHttpClient());
    }

    PasswordResetMailer(ObjectProvider<JavaMailSender> sender, String from, String host, String brevoKey, RestClient http) {
        this.sender = sender;
        this.from = from;
        this.host = host;
        this.brevoKey = brevoKey;
        this.http = http;
    }

    private static RestClient newHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !brevoKey.isBlank() || (!host.isBlank() && sender.getIfAvailable() != null);
    }

    /** @return true when the message was handed to the SMTP server. */
    public boolean sendResetLink(String to, String username, String link, int minutes) {
        if (!brevoKey.isBlank()) {
            return sendWithBrevo(to, username, render(username, link, minutes));
        }
        JavaMailSender mailSender = isConfigured() ? sender.getIfAvailable() : null;
        if (mailSender == null) {
            log.warn("No email provider is configured; password reset email not sent");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(render(username, link, minutes), true);
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException ex) {
            log.warn("Could not send password reset email: {}", ex.getMessage());
            return false;
        }
    }

    private boolean sendWithBrevo(String to, String username, String html) {
        Matcher named = NAMED_ADDRESS.matcher(from);
        String senderName = named.matches() && !named.group(1).isBlank() ? named.group(1).trim() : "Echoes of Time";
        String senderEmail = named.matches() ? named.group(2).trim() : from.trim();
        try {
            http.post().uri(BREVO_URL).header("api-key", brevoKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender", Map.of("name", senderName, "email", senderEmail),
                            "to", List.of(Map.of("email", to, "name", username)),
                            "subject", SUBJECT, "htmlContent", html))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.warn("Brevo rejected the password reset email: {}", ex.getMessage());
            return false;
        }
    }

    static String render(String username, String link, int minutes) {
        String name = HtmlUtils.htmlEscape(username);
        String url = HtmlUtils.htmlEscape(link);
        return """
            <div style="margin:0;padding:32px 12px;background:#0b0f17;font-family:Georgia,serif;">
              <table role="presentation" align="center" width="100%%" style="max-width:520px;background:#141b2a;border:1px solid #c8a24a;border-radius:12px;">
                <tr><td style="padding:28px 32px;text-align:center;">
                  <p style="margin:0;color:#c8a24a;letter-spacing:4px;font-size:12px;">ECHOES OF TIME</p>
                  <h1 style="margin:12px 0 4px;color:#f1e6c8;font-size:24px;">El tiempo ha sido alterado</h1>
                  <p style="margin:0 0 20px;color:#8f9bb3;font-size:14px;">Se recibió una solicitud para restablecer tu contraseña</p>
                  <p style="color:#d8dde8;font-size:15px;line-height:1.6;text-align:left;">
                    Viajero <strong style="color:#f1e6c8;">%s</strong>, alguien pidió restablecer la contraseña de tu cuenta.
                    Si fuiste tú, forja una nueva desde el portal:</p>
                  <p style="margin:24px 0;"><a href="%s" style="display:inline-block;padding:12px 28px;background:#c8a24a;color:#0b0f17;text-decoration:none;font-weight:bold;border-radius:8px;">Restablecer contraseña</a></p>
                  <p style="color:#d8dde8;font-size:13px;line-height:1.6;text-align:left;">
                    Este enlace se desvanece en <strong>%d minutos</strong> y solo funciona una vez. Al cambiarla se cerrarán
                    todas tus sesiones (web y móvil) y tendrás que iniciar sesión de nuevo.</p>
                  <p style="color:#8f9bb3;font-size:12px;line-height:1.6;text-align:left;">
                    Si no solicitaste esto, ignora este mensaje: tu contraseña seguirá intacta.<br>
                    Si el botón no funciona, copia este enlace: <span style="word-break:break-all;color:#c8a24a;">%s</span></p>
                </td></tr>
              </table>
            </div>
            """.formatted(name, url, minutes, url);
    }
}

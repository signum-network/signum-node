package brs.deeplink;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DeeplinkGenerator {

    private static final String Protocol = "signum://";
    private static final String Version = "v1";
    private static final Charset DefaultCharset = StandardCharsets.UTF_8;
    private static final Integer MaxPayloadLength = 8192;

    private final Map<EncodeHintType, ErrorCorrectionLevel> hints = new EnumMap<>(EncodeHintType.class);

    public DeeplinkGenerator() {
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
    }

    public String generateDeepLink(String action, String base64Payload) throws UnsupportedEncodingException, IllegalArgumentException {

        final StringBuilder deeplinkBuilder = new StringBuilder(DeeplinkGenerator.Protocol);
        deeplinkBuilder.append(DeeplinkGenerator.Version);
        if (action != null) {
            deeplinkBuilder.append("?action=");
            deeplinkBuilder.append(action);
            if (base64Payload != null) {
                deeplinkBuilder.append("&payload=");
                String encodedPayload = URLEncoder.encode(base64Payload, DefaultCharset.toString());
                if (encodedPayload.length() > MaxPayloadLength) {
                    throw new IllegalArgumentException("Maximum Payload Length (" + MaxPayloadLength + ") exceeded");
                }
                deeplinkBuilder.append(encodedPayload);
            }
        }
        return deeplinkBuilder.toString();
    }

    public BufferedImage generateDeepLinkQrCode(String action, String base64Payload) throws UnsupportedEncodingException, IllegalArgumentException, WriterException {
        return generateQRCode(this.generateDeepLink(action, base64Payload));
    }

    private BufferedImage generateQRCode(String url) throws WriterException {
        final QRCodeWriter qrCodeWriter = new QRCodeWriter();
        return MatrixToImageWriter.toBufferedImage(qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, 350, 350, hints), new MatrixToImageConfig());
    }
}


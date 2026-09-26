package com.smartbudget.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.smartbudget.dto.OpenAiRawInvoiceResponse;

@Service
public class OpenAiInvoiceExtractionService {

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";

    private final ObjectMapper objectMapper;
    private final S3DownloadUrlService s3DownloadUrlService;

    private final HttpClient httpClient;

    private final String apiKey;
    private final String model;

    public OpenAiInvoiceExtractionService(
            ObjectMapper objectMapper,
            S3DownloadUrlService s3DownloadUrlService,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model:gpt-5.6}") String model) {

        this.objectMapper = objectMapper;
        this.s3DownloadUrlService = s3DownloadUrlService;
        this.apiKey = apiKey;
        this.model = model;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public OpenAiRawInvoiceResponse extract(List<String> s3Keys) {

        if (s3Keys == null || s3Keys.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one invoice S3 key is required.");
        }

        try {

            ObjectNode requestBody = buildRequest(s3Keys);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_RESPONSES_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header(
                            "Authorization",
                            "Bearer " + apiKey)
                    .header(
                            "Content-Type",
                            "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(
                                            requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IllegalStateException(
                        "OpenAI API returned status "
                                + response.statusCode()
                                + ": "
                                + response.body());
            }

            return parseResponse(response.body());

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "OpenAI invoice extraction was interrupted.",
                    e);

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to call OpenAI invoice extraction.",
                    e);
        }
    }

    private ObjectNode buildRequest(List<String> s3Keys) {

        ObjectNode root = objectMapper.createObjectNode();

        root.put("model", model);

        // We don't need OpenAI to maintain conversation state
        // for invoice extraction.
        root.put("store", false);

        ArrayNode input = root.putArray("input");

        ObjectNode message = input.addObject();

        message.put("role", "user");

        ArrayNode content = message.putArray("content");

        ObjectNode instructions = content.addObject();

        instructions.put("type", "input_text");
        instructions.put("text", extractionInstructions());

        /*
         * Keep physical invoice pages in page order.
         *
         * Existing SmartBudget keys look like:
         *
         * page-1.jpg
         * page-2.jpg
         *
         * For the moment we preserve the order supplied by
         * the caller.
         */
        for (String s3Key : s3Keys) {

            String url = s3DownloadUrlService
                    .createPresignedGetUrl(s3Key);

            addDocumentContent(
                    content,
                    s3Key,
                    url);
        }

        ObjectNode text = root.putObject("text");

        ObjectNode format = text.putObject("format");

        format.put(
                "type",
                "json_schema");

        format.put(
                "name",
                "smartbudget_invoice");

        format.put(
                "strict",
                true);

        format.set(
                "schema",
                invoiceSchema());

        return root;
    }

    private void addDocumentContent(
            ArrayNode content,
            String s3Key,
            String url) {

        String lower = s3Key.toLowerCase();

        if (lower.endsWith(".pdf")) {

            ObjectNode file = content.addObject();

            file.put(
                    "type",
                    "input_file");

            file.put(
                    "file_url",
                    url);

            file.put(
                    "detail",
                    "high");

            return;
        }

        if (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")) {

            ObjectNode image = content.addObject();

            image.put(
                    "type",
                    "input_image");

            image.put(
                    "image_url",
                    url);

            image.put(
                    "detail",
                    "high");

            return;
        }

        throw new IllegalArgumentException(
                "Unsupported invoice file type: "
                        + s3Key);
    }

    private OpenAiRawInvoiceResponse parseResponse(
            String responseBody)
            throws IOException {

        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode output = root.path("output");

        for (JsonNode outputItem : output) {

            if (!"message".equals(
                    outputItem
                            .path("type")
                            .asString())) {

                continue;
            }

            for (JsonNode contentItem : outputItem.path("content")) {

                if ("output_text".equals(
                        contentItem
                                .path("type")
                                .asString())) {

                    String json = contentItem
                            .path("text")
                            .asString();

                    return objectMapper.readValue(
                            json,
                            OpenAiRawInvoiceResponse.class);
                }
            }
        }

        throw new IllegalStateException(
                "OpenAI response contained no output_text.");
    }

    private String extractionInstructions() {

        return """
                Extract the supplied invoice exactly as visibly printed.

                The supplied files/pages belong to ONE invoice.

                Rules:

                - This is transcription and structured data extraction.
                - Never guess a missing value.
                - Never invent a value.
                - Never repair a product code.
                - Never calculate a missing financial value.
                - If a value is unclear or not visibly present, return null.
                - Preserve line item order exactly as printed.
                - Do not include section headings such as FROZEN, CHILLER or DRY.
                - Do not include subtotal, total, signature or footer rows as products.
                - Do not shift a value from one product row into another row.

                PRODUCT CODE:
                - Transcribe the code character-by-character.
                - Do not autocorrect the code.
                - If any character is genuinely unclear, return null.

                QUANTITY AND PRICE:
                - quantity is the printed supplied/purchased quantity.
                - unitPrice is the printed unit price.
                - If the document contains both "Unit Price" and another column called
                  "Price", use the actual Unit Price column as unitPrice.
                - Do not treat a standalone Price column as the line amount.

                GENERIC AMOUNT:
                - Use amount ONLY when the invoice literally contains a generic
                  Amount or Value column.
                - Do not reinterpret a generic Amount as lineExGst or lineTotal.
                - Even if GST is zero, keep a generic Amount in amount.

                GST-SPECIFIC COLUMNS:
                - Populate lineExGst only if the invoice explicitly contains an
                  Ex GST, Excl GST, Net or equivalent line-value column.
                - Populate gstValue only when a line GST/tax value is explicitly printed.
                - Populate lineTotal only when a GST-inclusive line total/value is
                  explicitly printed.
                - Do not calculate any of these fields.

                INVOICE TOTALS:
                - subtotalExGst should only be populated from an explicitly printed
                  subtotal/ex-GST/net invoice total.
                - gst should only come from a printed GST/tax summary.
                - total should only come from a printed invoice total.
                - Never derive one summary value from another.

                DATE:
                - Return invoiceDate as YYYY-MM-DD when the printed invoice date is
                  unambiguous.

                PAGE:
                - pageNumber is the page where the product row appears.
                - Pages are supplied in document/page order.
                """;
    }

    private JsonNode invoiceSchema() {

        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "supplier": {
                      "type": ["string", "null"]
                    },
                    "invoiceNumber": {
                      "type": ["string", "null"]
                    },
                    "invoiceDate": {
                      "type": ["string", "null"]
                    },
                    "subtotalExGst": {
                      "type": ["number", "null"]
                    },
                    "gst": {
                      "type": ["number", "null"]
                    },
                    "total": {
                      "type": ["number", "null"]
                    },
                    "items": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "productCode": {
                            "type": ["string", "null"]
                          },
                          "description": {
                            "type": ["string", "null"]
                          },
                          "brand": {
                            "type": ["string", "null"]
                          },
                          "packSize": {
                            "type": ["string", "null"]
                          },
                          "unit": {
                            "type": ["string", "null"]
                          },
                          "quantity": {
                            "type": ["number", "null"]
                          },
                          "unitPrice": {
                            "type": ["number", "null"]
                          },
                          "amount": {
                            "type": ["number", "null"]
                          },
                          "lineExGst": {
                            "type": ["number", "null"]
                          },
                          "gstValue": {
                            "type": ["number", "null"]
                          },
                          "lineTotal": {
                            "type": ["number", "null"]
                          },
                          "pageNumber": {
                            "type": ["integer", "null"]
                          }
                        },
                        "required": [
                          "productCode",
                          "description",
                          "brand",
                          "packSize",
                          "unit",
                          "quantity",
                          "unitPrice",
                          "amount",
                          "lineExGst",
                          "gstValue",
                          "lineTotal",
                          "pageNumber"
                        ],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": [
                    "supplier",
                    "invoiceNumber",
                    "invoiceDate",
                    "subtotalExGst",
                    "gst",
                    "total",
                    "items"
                  ],
                  "additionalProperties": false
                }
                """;

        return objectMapper.readTree(schema);

    }
}
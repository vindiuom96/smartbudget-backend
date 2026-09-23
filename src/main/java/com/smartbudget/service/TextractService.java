package com.smartbudget.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.dto.ExtractedField;
import com.smartbudget.dto.ExtractedLineItem;
import com.smartbudget.dto.TextractResultResponse;
import com.smartbudget.dto.TextractStartResponse;
import com.smartbudget.dto.TextractTableResultResponse;
import com.smartbudget.dto.TextractBlockDto;
import com.smartbudget.dto.TextractRelationshipDto;

import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetExpenseAnalysisResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.LineItemFields;
import software.amazon.awssdk.services.textract.model.LineItemGroup;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartExpenseAnalysisResponse;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;

@Service
public class TextractService {

    private final TextractClient textractClient;
    private final String bucketName;

    public TextractService(
            TextractClient textractClient,
            @Value("${aws.s3.bucket}") String bucketName) {

        this.textractClient = textractClient;
        this.bucketName = bucketName;
    }

    public TextractStartResponse startAnalysis(
            String userSub,
            String invoiceDraftId,
            String s3Key) {

        validateS3Key(userSub, invoiceDraftId, s3Key);

        S3Object s3Object = S3Object.builder()
                .bucket(bucketName)
                .name(s3Key)
                .build();

        DocumentLocation documentLocation = DocumentLocation.builder()
                .s3Object(s3Object)
                .build();

        StartExpenseAnalysisRequest request = StartExpenseAnalysisRequest.builder()
                .documentLocation(documentLocation)
                .build();

        StartExpenseAnalysisResponse response = textractClient.startExpenseAnalysis(request);

        return new TextractStartResponse(
                invoiceDraftId,
                response.jobId());
    }

    public TextractResultResponse getAnalysis(
            String jobId) {

        GetExpenseAnalysisResponse firstResponse = textractClient.getExpenseAnalysis(
                GetExpenseAnalysisRequest.builder()
                        .jobId(jobId)
                        .maxResults(1000)
                        .build());

        String status = firstResponse.jobStatusAsString();

        if (firstResponse.jobStatus() != JobStatus.SUCCEEDED) {

            return new TextractResultResponse(
                    status,
                    firstResponse.statusMessage(),
                    List.of(),
                    List.of());
        }

        List<ExpenseDocument> expenseDocuments = new ArrayList<>(
                firstResponse.expenseDocuments());

        String nextToken = firstResponse.nextToken();

        while (nextToken != null) {

            GetExpenseAnalysisResponse nextResponse = textractClient.getExpenseAnalysis(
                    GetExpenseAnalysisRequest.builder()
                            .jobId(jobId)
                            .maxResults(1000)
                            .nextToken(nextToken)
                            .build());

            expenseDocuments.addAll(
                    nextResponse.expenseDocuments());

            nextToken = nextResponse.nextToken();
        }

        List<ExtractedField> summaryFields = extractSummaryFields(expenseDocuments);

        List<ExtractedLineItem> lineItems = extractLineItems(expenseDocuments);

        return new TextractResultResponse(
                status,
                firstResponse.statusMessage(),
                summaryFields,
                lineItems);
    }

    public TextractTableResultResponse getTableAnalysis(
            String jobId) {

        List<TextractBlockDto> allBlocks = new ArrayList<>();

        String nextToken = null;

        String statusMessage = null;

        JobStatus finalStatus = null;

        do {

            GetDocumentAnalysisRequest.Builder requestBuilder = GetDocumentAnalysisRequest.builder()
                    .jobId(jobId)
                    .maxResults(1000);

            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            GetDocumentAnalysisResponse response = textractClient.getDocumentAnalysis(
                    requestBuilder.build());

            finalStatus = response.jobStatus();

            statusMessage = response.statusMessage();

            /*
             * If Textract hasn't finished yet,
             * don't try to parse blocks.
             */
            if (finalStatus == JobStatus.IN_PROGRESS) {

                return new TextractTableResultResponse(
                        "IN_PROGRESS",
                        statusMessage,
                        List.of());
            }

            if (finalStatus == JobStatus.FAILED) {

                return new TextractTableResultResponse(
                        "FAILED",
                        statusMessage,
                        List.of());
            }

            allBlocks.addAll(
                    response.blocks().stream()
                            .map(this::toBlockDto)
                            .toList());

            nextToken = response.nextToken();

        } while (nextToken != null
                && !nextToken.isBlank());

        return new TextractTableResultResponse(
                finalStatus != null
                        ? finalStatus.toString()
                        : "UNKNOWN",
                statusMessage,
                allBlocks);
    }

    private List<ExtractedField> extractSummaryFields(
            List<ExpenseDocument> documents) {

        List<ExtractedField> result = new ArrayList<>();

        for (ExpenseDocument document : documents) {

            for (ExpenseField field : document.summaryFields()) {

                result.add(toExtractedField(field));
            }
        }

        return result;
    }

    public String startTableAnalysis(String s3Key) {

        S3Object s3Object = S3Object.builder()
                .bucket(bucketName)
                .name(s3Key)
                .build();

        DocumentLocation documentLocation = DocumentLocation.builder()
                .s3Object(s3Object)
                .build();

        StartDocumentAnalysisRequest request = StartDocumentAnalysisRequest.builder()
                .documentLocation(documentLocation)
                .featureTypes(FeatureType.TABLES)
                .build();

        StartDocumentAnalysisResponse response = textractClient.startDocumentAnalysis(request);

        return response.jobId();
    }

    private TextractBlockDto toBlockDto(Block block) {

        List<TextractRelationshipDto> relationships = block.relationships()
                .stream()
                .map(relationship -> new TextractRelationshipDto(
                        relationship.typeAsString(),
                        relationship.ids()))
                .toList();

        return new TextractBlockDto(
                block.id(),
                block.blockTypeAsString(),
                block.text(),
                block.page(),
                block.rowIndex(),
                block.columnIndex(),
                block.rowSpan(),
                block.columnSpan(),
                block.confidence(),
                block.entityTypesAsStrings(),
                relationships);
    }

    private List<ExtractedLineItem> extractLineItems(
            List<ExpenseDocument> documents) {

        List<ExtractedLineItem> result = new ArrayList<>();

        for (ExpenseDocument document : documents) {

            for (LineItemGroup group : document.lineItemGroups()) {

                for (LineItemFields lineItem : group.lineItems()) {

                    List<ExtractedField> fields = lineItem
                            .lineItemExpenseFields()
                            .stream()
                            .map(this::toExtractedField)
                            .toList();

                    result.add(
                            new ExtractedLineItem(fields));
                }
            }
        }

        return result;
    }

    private ExtractedField toExtractedField(
            ExpenseField field) {

        String type = field.type() != null
                ? field.type().text()
                : null;

        String label = field.labelDetection() != null
                ? field.labelDetection().text()
                : null;

        String value = field.valueDetection() != null
                ? field.valueDetection().text()
                : null;

        Float confidence = field.valueDetection() != null
                ? field.valueDetection().confidence()
                : null;

        return new ExtractedField(
                type,
                label,
                value,
                confidence,
                field.pageNumber());
    }

    private void validateS3Key(
            String userSub,
            String invoiceDraftId,
            String s3Key) {

        String requiredPrefix = "invoices/"
                + userSub
                + "/"
                + invoiceDraftId
                + "/";

        if (s3Key == null
                || !s3Key.startsWith(requiredPrefix)) {

            throw new IllegalArgumentException(
                    "Invalid invoice S3 key");
        }
    }
}
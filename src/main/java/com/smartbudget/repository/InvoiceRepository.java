package com.smartbudget.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.smartbudget.model.InvoiceEntity;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;

@Repository
public class InvoiceRepository {

    private final DynamoDbTable<InvoiceEntity> invoiceTable;

    public InvoiceRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.invoice-table-name}") String tableName) {

        this.invoiceTable = enhancedClient.table(
                tableName,
                TableSchema.fromBean(
                        InvoiceEntity.class));
    }

    public void save(InvoiceEntity invoice) {

        Expression notAlreadyExists = Expression.builder()
                .expression(
                        "attribute_not_exists(sortKey)")
                .build();

        PutItemEnhancedRequest<InvoiceEntity> request = PutItemEnhancedRequest
                .builder(InvoiceEntity.class)
                .item(invoice)
                .conditionExpression(
                        notAlreadyExists)
                .build();

        invoiceTable.putItem(request);
    }

    public InvoiceEntity findByUserAndSortKey(
            String userSub,
            String sortKey) {

        Key key = Key.builder()
                .partitionValue(userSub)
                .sortValue(sortKey)
                .build();

        return invoiceTable.getItem(key);
    }

    public List<InvoiceEntity> findAllByUser(
            String userSub) {

        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue(userSub)
                        .build());

        return invoiceTable
                .query(condition)
                .items()
                .stream()
                .toList();
    }
}
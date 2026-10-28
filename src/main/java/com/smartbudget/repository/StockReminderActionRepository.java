package com.smartbudget.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.smartbudget.model.StockReminderActionEntity;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class StockReminderActionRepository {

    private final DynamoDbTable<StockReminderActionEntity> reminderActionTable;

    public StockReminderActionRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.reminder-action-table-name}") String tableName) {

        this.reminderActionTable = enhancedClient.table(
                tableName,
                TableSchema.fromBean(
                        StockReminderActionEntity.class));
    }

    public void save(
            StockReminderActionEntity action) {

        /*
         * Deliberately use normal putItem.
         *
         * There should only be one action per
         * reminder occurrence. If that occurrence
         * is written again, the latest action wins.
         */
        reminderActionTable.putItem(
                action);
    }

    public StockReminderActionEntity findByUserAndSortKey(
            String userSub,
            String sortKey) {

        Key key = Key.builder()
                .partitionValue(
                        userSub)
                .sortValue(
                        sortKey)
                .build();

        return reminderActionTable.getItem(
                key);
    }

    public List<StockReminderActionEntity> findAllByUser(
            String userSub) {

        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue(
                                userSub)
                        .build());

        return reminderActionTable
                .query(condition)
                .items()
                .stream()
                .toList();
    }
}
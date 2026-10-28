package com.smartbudget.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class StockReminderActionEntity {

    private String userSub;
    private String sortKey;

    private String supplierId;
    private String productCode;
    private String expectedNextDate;

    private String actionType;
    private String actionAt;

    @DynamoDbPartitionKey
    public String getUserSub() {
        return userSub;
    }

    public void setUserSub(String userSub) {
        this.userSub = userSub;
    }

    @DynamoDbSortKey
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getExpectedNextDate() {
        return expectedNextDate;
    }

    public void setExpectedNextDate(
            String expectedNextDate) {

        this.expectedNextDate = expectedNextDate;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(
            String actionType) {

        this.actionType = actionType;
    }

    public String getActionAt() {
        return actionAt;
    }

    public void setActionAt(
            String actionAt) {

        this.actionAt = actionAt;
    }
}
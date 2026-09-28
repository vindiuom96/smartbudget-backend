package com.smartbudget.model;

import java.math.BigDecimal;
import java.util.List;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class InvoiceEntity {

    private String userSub;
    private String sortKey;

    private String invoiceId;
    private String invoiceDraftId;

    private String supplierId;
    private String supplierName;
    private String rawSupplierName;
    private String invoiceNumber;
    private String invoiceDate;

    private BigDecimal subtotalExGst;
    private BigDecimal gst;
    private BigDecimal total;

    private List<String> s3Keys;
    private List<InvoiceLineItemEntity> items;

    private String approvedAt;

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

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceDraftId() {
        return invoiceDraftId;
    }

    public void setInvoiceDraftId(String invoiceDraftId) {
        this.invoiceDraftId = invoiceDraftId;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getRawSupplierName() {
        return rawSupplierName;
    }

    public void setRawSupplierName(String rawSupplierName) {
        this.rawSupplierName = rawSupplierName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public BigDecimal getSubtotalExGst() {
        return subtotalExGst;
    }

    public void setSubtotalExGst(BigDecimal subtotalExGst) {
        this.subtotalExGst = subtotalExGst;
    }

    public BigDecimal getGst() {
        return gst;
    }

    public void setGst(BigDecimal gst) {
        this.gst = gst;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<String> getS3Keys() {
        return s3Keys;
    }

    public void setS3Keys(List<String> s3Keys) {
        this.s3Keys = s3Keys;
    }

    public List<InvoiceLineItemEntity> getItems() {
        return items;
    }

    public void setItems(List<InvoiceLineItemEntity> items) {
        this.items = items;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(String approvedAt) {
        this.approvedAt = approvedAt;
    }
}
package com.smartbudget.model;

import java.math.BigDecimal;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class InvoiceLineItemEntity {

    private String productCode;
    private String description;
    private String brand;
    private String packSize;
    private String unit;

    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineExGst;
    private BigDecimal gstValue;
    private BigDecimal lineTotal;

    private Integer pageNumber;

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getPackSize() {
        return packSize;
    }

    public void setPackSize(String packSize) {
        this.packSize = packSize;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineExGst() {
        return lineExGst;
    }

    public void setLineExGst(BigDecimal lineExGst) {
        this.lineExGst = lineExGst;
    }

    public BigDecimal getGstValue() {
        return gstValue;
    }

    public void setGstValue(BigDecimal gstValue) {
        this.gstValue = gstValue;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }
}
package com.kaadas.scraper.model;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 工单实体类
 * 对应原项目中的 GbOrder
 */
public class GbOrder {
    private String orderNo;           // 系统生成的订单号
    private String promoter;          // 源订单号 (workNo)
    private String workOrderType;     // 工单类型
    private String workOrderTypeCode; // 工单类型编码
    private String userName;          // 用户姓名
    private String userTelephone;     // 用户电话
    private String provinceName;      // 省
    private String cityName;          // 市
    private String areaName;          // 区
    private String provinceCode;      // 省编码
    private String cityCode;          // 市编码
    private String areaCode;          // 区编码
    private String address;           // 详细地址
    private String fullAddress;       // 完整地址
    private String workRemark;        // 需求描述/商品信息
    private String orderLevelName;    // 级别名称
    private String orderLevelCode;    // 级别编码
    private BigDecimal orderLevelValue; // 级别分值
    private BigDecimal originalAmount;  // 原定金额
    private BigDecimal subsidyAmount;   // 补贴金额
    private BigDecimal revenue;         // 收入
    private BigDecimal responseTime;    // 响应时间
    private Date reqDate;               // 请求日期
    private String supplyName;        // 供应商名称

    // Getters and Setters
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPromoter() { return promoter; }
    public void setPromoter(String promoter) { this.promoter = promoter; }
    public String getWorkOrderType() { return workOrderType; }
    public void setWorkOrderType(String workOrderType) { this.workOrderType = workOrderType; }
    public String getWorkOrderTypeCode() { return workOrderTypeCode; }
    public void setWorkOrderTypeCode(String workOrderTypeCode) { this.workOrderTypeCode = workOrderTypeCode; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserTelephone() { return userTelephone; }
    public void setUserTelephone(String userTelephone) { this.userTelephone = userTelephone; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getFullAddress() { return fullAddress; }
    public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }
    public String getWorkRemark() { return workRemark; }
    public void setWorkRemark(String workRemark) { this.workRemark = workRemark; }
    public String getOrderLevelName() { return orderLevelName; }
    public void setOrderLevelName(String orderLevelName) { this.orderLevelName = orderLevelName; }
    public String getOrderLevelCode() { return orderLevelCode; }
    public void setOrderLevelCode(String orderLevelCode) { this.orderLevelCode = orderLevelCode; }
    public BigDecimal getOrderLevelValue() { return orderLevelValue; }
    public void setOrderLevelValue(BigDecimal orderLevelValue) { this.orderLevelValue = orderLevelValue; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getSubsidyAmount() { return subsidyAmount; }
    public void setSubsidyAmount(BigDecimal subsidyAmount) { this.subsidyAmount = subsidyAmount; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public BigDecimal getResponseTime() { return responseTime; }
    public void setResponseTime(BigDecimal responseTime) { this.responseTime = responseTime; }
    public Date getReqDate() { return reqDate; }
    public void setReqDate(Date reqDate) { this.reqDate = reqDate; }
    public String getSupplyName() { return supplyName; }
    public void setSupplyName(String supplyName) { this.supplyName = supplyName; }
}

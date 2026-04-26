package com.kaadas.scraper.dao;

import com.kaadas.scraper.model.GbOrder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 数据库操作类 (JDBC 实现)
 * 对应原项目中的 gbOrderService.saveInfo
 */
public class WorkOrderDao {
    private String url;
    private String user;
    private String password;

    public WorkOrderDao(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * 检查订单是否已存在（根据 promoter 源订单号）
     * 对应原项目中的 gbOrderService.getInfoByPromoter
     */
    public boolean exists(String promoter) {
        String sql = "SELECT count(1) FROM gb_order WHERE promoter = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, promoter);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 保存订单到数据库
     * 对应原项目中的 gbOrderService.saveInfo(gbOrder, GlobleParams.GbOrderType.GB_ORDER_TYPE_7.getValue())
     * @param order 订单实体
     * @param orderType 订单类型 (4 或 7 代表凯迪仕渠道)
     */
    public void save(GbOrder order, int orderType) {
        String sql = "INSERT INTO gb_order (" +
                     "order_no, promoter, work_order_type, work_order_type_code, user_name, user_telephone, " +
                     "provincename, cityname, areaname, provincecode, citycode, areacode, address, fulladdress, " +
                     "workRemark, orderLevelName, orderLevelCode, orderLevelValue, original_amount, subsidy_amount, revenue, " +
                     "responseTime, req_date, supplyName, order_type, del_flag, status, order_date, isPay, user_evaluate, evaluate, CreateTime" +
                     ") VALUES (" +
                     "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                     "0, 0, NOW(), 0, -1, -1, NOW()" +
                     ")";

        // 生成凯迪仕订单号 (KDS + yyyyMMddHHmmss + 3位随机数)
        // 对应原项目中的订单号生成逻辑
        String localOrderNo = "KDS" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + (int)((Math.random()*9+1)*100);
        order.setOrderNo(localOrderNo);

        // 设置默认值（对应原项目中的默认值设置）
        if (order.getResponseTime() == null) {
            order.setResponseTime(new BigDecimal(0));
        }
        if (order.getSubsidyAmount() == null) {
            order.setSubsidyAmount(new BigDecimal(0));
        }
        if (order.getRevenue() == null) {
            order.setRevenue(new BigDecimal(0));
        }
        if (order.getReqDate() == null) {
            order.setReqDate(new Date());
        }

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, localOrderNo);
            ps.setString(i++, order.getPromoter());
            ps.setString(i++, order.getWorkOrderType());
            ps.setString(i++, order.getWorkOrderTypeCode());
            ps.setString(i++, order.getUserName());
            ps.setString(i++, order.getUserTelephone());
            ps.setString(i++, order.getProvinceName());
            ps.setString(i++, order.getCityName());
            ps.setString(i++, order.getAreaName());
            ps.setString(i++, order.getProvinceCode());
            ps.setString(i++, order.getCityCode());
            ps.setString(i++, order.getAreaCode());
            ps.setString(i++, order.getAddress());
            ps.setString(i++, order.getFullAddress());
            ps.setString(i++, order.getWorkRemark());
            ps.setString(i++, order.getOrderLevelName());
            ps.setString(i++, order.getOrderLevelCode());
            ps.setBigDecimal(i++, order.getOrderLevelValue());
            ps.setBigDecimal(i++, order.getOriginalAmount());
            ps.setBigDecimal(i++, order.getSubsidyAmount());
            ps.setBigDecimal(i++, order.getRevenue());
            ps.setBigDecimal(i++, order.getResponseTime());
            ps.setTimestamp(i++, new java.sql.Timestamp(order.getReqDate().getTime()));
            ps.setString(i++, order.getSupplyName());
            ps.setInt(i, orderType);

            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

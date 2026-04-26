package com.kaadas.scraper;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.server.HttpServerRequest;
import cn.hutool.http.server.HttpServerResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kaadas.scraper.dao.WorkOrderDao;
import com.kaadas.scraper.model.GbOrder;
import com.kaadas.scraper.utils.AddressCodeUtil;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 凯迪仕工单独立抓取程序
 * 负责从凯迪仕平台抓取待处理工单并存入本地数据库
 * 提供 HTTP 服务接口，默认端口 2333
 *
 * 对应原项目: /Users/mima0000/wzc/gitwzc/ldAdminAPI/system-db-service/src/main/java/com/system/db/service/gb/KDSOrderService.java
 */
public class KaadasScraperApp {

    // HTTP 服务端口
    private static final int SERVER_PORT = 2333;

    // 凯迪仕 API 接口地址
    private static final String LOGIN_URL = "https://webapp.kaadas.com/agent/api/AgentPCApp/AgentCommon/Login";
    private static final String ORDER_LIST_URL = "https://webapp.kaadas.com/agent/api/AgentPCApp/AgentWorkInfo/SreachWorkInfo";
    private static final String ORDER_PRODUCT_URL = "https://webapp.kaadas.com/agent/api/AgentPCApp/AgentWorkInfo/Products";
    private static final String ORDER_DECODE_URL = "https://webapp.kaadas.com/agent/api/AgentPCApp/AgentWorkInfo/SensitiveMsg";

    // AI 地址清洗 API 配置 (DeepSeek)
    private static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_KEY = "sk-6b8a1425b13b4f6ebcd7097bfcf836eb";

    // 数据库连接配置
    private static final String DB_URL = "jdbc:mysql://localhost:3306/greenbeanlock?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=GMT%2b8";
//    private static final String DB_USER = "root";
//    private static final String DB_PASS = "123456";
    private static final String DB_USER = "greenbeanlock";
    private static final String DB_PASS = "GzNNGBWzNMmBc6mH";

    private final WorkOrderDao dao;
    private volatile boolean isRunning = false;
    private volatile String lastRunTime = "从未运行";
    private volatile String lastRunResult = "";

    public KaadasScraperApp() {
        this.dao = new WorkOrderDao(DB_URL, DB_USER, DB_PASS);
    }

    /**
     * 开始执行一次完整的抓取流程
     */
    public void start() {
        if (isRunning) {
            System.out.println("[WARNING] 抓取任务已在运行中，跳过本次执行");
            return;
        }

        isRunning = true;
        String now = DateUtil.now();
        lastRunTime = now;

        System.out.println("\n================================================================================");
        System.out.println(">>> [" + now + "] 凯迪仕抓取任务开始执行...");
        System.out.println("================================================================================");

        try {
            // 1. 登录获取 Token
            String token = loginData();
            if (token == null) {
                System.err.println("[" + now + "] ERROR: 登录失败，请检查账号密码或验证码。");
                lastRunResult = "登录失败";
                return;
            }

            // 2. 获取待处理工单列表 (状态 1 表示待处理)
            JSONArray dataList = getDataList(token, "1");
            if (dataList == null || dataList.isEmpty()) {
                System.out.println("[" + now + "] INFO: 当前没有待处理工单。");
                lastRunResult = "无待处理工单";
                return;
            }

            int totalCount = dataList.size();
            int successCount = 0;
            int existCount = 0;
            int failCount = 0;

            System.out.println("[" + now + "] INFO: 发现待处理工单数量: " + totalCount);

            // 3. 遍历并处理每个工单
            for (int i = 0; i < totalCount; i++) {
                JSONObject item = dataList.getJSONObject(i);
                String workNo = item.getStr("No");

                try {
                    // 检查数据库中是否已存在该源订单号
                    // 对应原项目: gbOrderService.getInfoByPromoter(gbOrder.getPromoter())
                    if (dao.exists(workNo)) {
                        System.out.println("  - [" + (i+1) + "/" + totalCount + "] 工单已存在跳过: " + workNo);
                        existCount++;
                        continue;
                    }

                    System.out.print("  - [" + (i+1) + "/" + totalCount + "] 正在处理工单: " + workNo + " ... ");
                    processOrder(item, token);
                    successCount++;
                    System.out.println("成功 √");
                } catch (Exception e) {
                    failCount++;
                    System.out.println("失败 ×");
                    System.err.println("    原因: " + e.getMessage());
                }
                // 频率控制，防止被封 IP
                Thread.sleep(100);
            }

            // 4. 打印本次任务总结
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println(">>> [" + DateUtil.now() + "] 任务执行完毕总结:");
            System.out.println("    总计: " + totalCount + " | 新增: " + successCount + " | 已存在: " + existCount + " | 失败: " + failCount);
            System.out.println("================================================================================\n");

            lastRunResult = "总计:" + totalCount + " 新增:" + successCount + " 已存在:" + existCount + " 失败:" + failCount;

        } catch (Exception e) {
            System.err.println("[" + now + "] CRITICAL ERROR: 任务执行过程中发生异常!");
            e.printStackTrace();
            lastRunResult = "执行异常: " + e.getMessage();
        } finally {
            isRunning = false;
        }
    }

    /**
     * 处理单个工单详情并保存到数据库
     * 对应原项目: executeOrder 方法和 checkOrder 方法
     * @param data 列表中的简略工单数据
     * @param token 登录令牌
     */
    private void processOrder(JSONObject data, String token) throws Exception {
        String workNo = data.getStr("No");
        GbOrder gbOrder = new GbOrder();

        // 1. 设置源订单号
        // 对应原项目: gbOrder.setPromoter(data.getWorkNo())
        gbOrder.setPromoter(workNo);

        // 2. 类型映射：将凯迪仕的 SetupType 映射到系统内部类型
        // 对应原项目 checkOrder 方法中的类型判断逻辑
        String setupType = data.getStr("SetupType", "1");
        if ("1".equals(setupType)) {
            gbOrder.setWorkOrderType("安装工单");
            gbOrder.setWorkOrderTypeCode("WORK_ORDER_TYPE_1");
        } else if ("2".equals(setupType)) {
            gbOrder.setWorkOrderType("维修工单");
            gbOrder.setWorkOrderTypeCode("WORK_ORDER_TYPE_3");
        } else if ("9".equals(setupType)) {
            gbOrder.setWorkOrderType("勘测工单");
            gbOrder.setWorkOrderTypeCode("WORK_ORDER_TYPE_5");
        } else {
            gbOrder.setWorkOrderType("安装工单");
            gbOrder.setWorkOrderTypeCode("WORK_ORDER_TYPE_1");
        }

        // 3. 解密敏感信息：调用凯迪仕接口获取解密后的手机号和姓名
        // 对应原项目: this.decodeData(token, "2", workNo) 和 this.decodeData(token, "1", workNo)
        String phone = this.decodeData(token, "2", workNo);
        String name = this.decodeData(token, "1", workNo);
        gbOrder.setUserTelephone(phone);
        gbOrder.setUserName(name);

        // 处理带分机的手机号，如 "138...-801"
        // 对应原项目 executeOrder 方法中的处理逻辑
        if (gbOrder.getUserTelephone() != null && gbOrder.getUserTelephone().contains("-")) {
            String[] phoneArray = gbOrder.getUserTelephone().split("-");
            gbOrder.setUserTelephone(phoneArray[0]);
            gbOrder.setUserName(gbOrder.getUserName() + "(-" + phoneArray[1] + ")");
        }

        // 4. 获取商品和金额信息
        // 对应原项目: getProductData(token, workNo)
        Pair<BigDecimal, String> productData = getProductData(token, workNo);
        gbOrder.setWorkRemark(productData.getValue());
        gbOrder.setOriginalAmount(productData.getKey());

        // 设置补贴金额和收入（默认值0）
        // 对应原项目 executeOrder 方法中的设置
        gbOrder.setSubsidyAmount(new BigDecimal(0));
        gbOrder.setRevenue(new BigDecimal(0));

        // 5. 地址获取与清洗
        // 优先尝试解密获取详细地址，如果没有则使用列表中的全路径地址
        // 对应原项目: this.decodeData(token, "3", workNo)
        String rawAddress = this.decodeData(token, "3", workNo);
        if (rawAddress == null || rawAddress.isEmpty()) {
            rawAddress = data.getStr("SetupAddressFull");
        }

        // 使用 DeepSeek 进行 AI 地址清洗
        // 对应原项目 executeOrder 方法中的 deepSeekService.getDeepSeekResult 调用
        cleanAddressWithDeepSeek(gbOrder, rawAddress);

        // 6. 设置工单级别信息
        // 对应原项目: orderLevel.getName(), orderLevel.getVal(), orderLevel.getNumber()
        gbOrder.setOrderLevelName("一级工单");
        gbOrder.setOrderLevelCode("WORK_ORDER_LEVEL_1");
        gbOrder.setOrderLevelValue(new BigDecimal("10"));

        // 设置响应时间（默认值0）
        // 对应原项目: gbOrder.setResponseTime(new BigDecimal(0))
        gbOrder.setResponseTime(new BigDecimal(0));

        // 设置请求日期
        // 对应原项目: gbOrder.setReqDate(new Date())
        gbOrder.setReqDate(new Date());

        // 设置供应商名称
        // 对应原项目: gbOrder.setSupplyName("凯迪仕")
        gbOrder.setSupplyName("凯迪仕");

        // 7. 调用 DAO 保存到数据库
        // 对应原项目: gbOrderService.saveInfo(gbOrder, GlobleParams.GbOrderType.GB_ORDER_TYPE_7.getValue())
        // 7 代表凯迪仕渠道
        dao.save(gbOrder, 7);
    }

    /**
     * 使用 DeepSeek API 对原始地址进行清洗和结构化处理
     * 对应原项目 executeOrder 方法中的地址处理逻辑
     * @param gbOrder 工单实体
     * @param rawAddress 原始地址字符串
     */
    private void cleanAddressWithDeepSeek(GbOrder gbOrder, String rawAddress) {
        System.out.println("\n    [DeepSeek] 正在清洗地址: " + rawAddress);
        try {
            // 提示词优化：明确要求使用标准 JSON 格式，并使用英文冒号
            String systemPrompt = "你是数据清洗专家，负责清洗地址中的省市区县和详细地址,如果不全请补充完整的省-市-区/县.如果实在匹配不上上完整的省市区就返回 无省市区 字样,如果省市区重复就只保留最准确的.必须严格返回JSON格式字符串: {\"area\": \"省-市-区/县\", \"address\": \"详细地址\"}";

            JSONObject requestBody = new JSONObject();
            requestBody.set("model", "deepseek-chat");
            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
            messages.add(new JSONObject().set("role", "user").set("content", rawAddress));
            requestBody.set("messages", messages);
            requestBody.set("stream", false);

            HttpResponse response = HttpRequest.post(DEEPSEEK_URL)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + DEEPSEEK_KEY)
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(15000)
                    .execute();

            if (response.getStatus() == 200) {
                JSONObject resJson = JSONUtil.parseObj(response.body());
                String content = resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getStr("content");

                System.out.println("    [DeepSeek] 原始解析内容: " + content.replace("\n", " "));

                // 鲁棒性处理：提取并修正 AI 返回的 JSON 字符串
                int start = content.indexOf("{");
                int end = content.lastIndexOf("}");
                if (start != -1 && end != -1) {
                    String jsonStr = content.substring(start, end + 1);
                    // 关键修复：将全角冒号替换为半角冒号，防止解析报错
                    jsonStr = jsonStr.replace("：", ":").replace("，", ",");

                    try {
                        JSONObject cleanResult = JSONUtil.parseObj(jsonStr);
                        String area = cleanResult.getStr("area");
                        String address = cleanResult.getStr("address");

                        if (!"无省市区".equals(area) && area != null && area.contains("-")) {
                            String[] areaArray = area.split("-");
                            if (areaArray.length >= 3) {
                                gbOrder.setProvinceName(areaArray[0]);
                                gbOrder.setCityName(areaArray[1]);
                                gbOrder.setAreaName(areaArray[2]);
                            }
                            gbOrder.setAddress(address);
                            gbOrder.setFullAddress(area.replace("-", " ") + " " + address);
                        }
                    } catch (Exception parseEx) {
                        System.err.println("    [DeepSeek] JSON 解析重试失败: " + parseEx.getMessage());
                        System.err.println("    [DeepSeek] 尝试解析的字符串: " + jsonStr);
                    }
                }
            } else {
                String errorMsg = response.body();
                System.err.println("    [DeepSeek] 请求异常，状态码: " + response.getStatus());
                if (response.getStatus() == 401) {
                    System.err.println("    [DeepSeek] 错误提示: API Key 无效或已过期，请检查 DEEPSEEK_KEY 配置。");
                } else if (response.getStatus() == 402) {
                    System.err.println("    [DeepSeek] 错误提示: 账户余额不足，请充值。");
                }
                System.err.println("    [DeepSeek] 响应内容: " + errorMsg);
            }
        } catch (Exception e) {
            System.err.println("    [DeepSeek] 清洗过程发生异常: " + e.getMessage());
            e.printStackTrace();
        }

        // 获取编码补救逻辑
        // 对应原项目: ReadJsonFileUtil.getCityCode(gbOrder.getProvinceName(), gbOrder.getCityName(), gbOrder.getAreaName())
        String[] codes = AddressCodeUtil.getCodes(gbOrder.getProvinceName(), gbOrder.getCityName(), gbOrder.getAreaName());
        gbOrder.setProvinceCode(codes[0]);
        gbOrder.setCityCode(codes[1]);
        gbOrder.setAreaCode(codes[2]);
        System.out.println("    [Address] 编码匹配结果: " + String.join(",", codes));
    }

    /**
     * 登录凯迪仕平台获取 Token
     * 对应原项目: loginData() 方法
     */
    private String loginData() throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("Phone", "13166830917");
        requestBody.put("Password", "487e251afe73e5e01c495e05b94a139e");
        requestBody.put("CaptchaKey", "966774A300504A99B3BCE24CF216EAC7111");
        requestBody.put("CaptchaCode", "12221");

        Thread.sleep(500);

        HttpResponse response = HttpRequest.post(LOGIN_URL)
                .body(JSONUtil.toJsonStr(requestBody))
                .timeout(10000)
                .execute();

        if (response.getStatus() == 200) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (jsonObject.getInt("Code") == 0) {
                return jsonObject.getJSONObject("Data").getStr("Token");
            }
        }
        return null;
    }

    /**
     * 获取工单列表数据
     * 对应原项目: getDataList 方法
     */
    private JSONArray getDataList(String token, String workStatus) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("WorkStatus", workStatus == null ? "1" : workStatus);
        requestBody.put("Page", 1);
        requestBody.put("Rows", 100);

        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("platform", "1");

        Thread.sleep(500);

        HttpResponse response = HttpRequest.post(ORDER_LIST_URL)
                .header("Authorization", "Bearer " + token)
                .header("platform", "1")
                .body(JSONUtil.toJsonStr(requestBody))
                .timeout(10000)
                .execute();

        if (response.getStatus() == 200) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (jsonObject.getInt("Code") == 0) {
                return jsonObject.getJSONObject("Data").getJSONArray("Items");
            }
        }
        return null;
    }

    /**
     * 调用凯迪仕敏感信息解密接口
     * 对应原项目: decodeData 方法
     * @param type 1: 姓名, 2: 电话, 3: 详细地址
     * @param workNo 工单号
     */
    private String decodeData(String token, String type, String workNo) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("workNo", workNo);

        Thread.sleep(100);

        HttpResponse response = HttpRequest.get(ORDER_DECODE_URL)
                .header("Authorization", "Bearer " + token)
                .header("platform", "1")
                .form(params)
                .timeout(10000)
                .execute();

        if (response.getStatus() == 200) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (jsonObject.getInt("Code") == 0) {
                return jsonObject.getStr("Data");
            }
        }
        return null;
    }

    /**
     * 获取工单关联的商品信息及总费用
     * 对应原项目: getProductData 方法
     */
    private Pair<BigDecimal, String> getProductData(String token, String workNo) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("No", workNo);

        Thread.sleep(100);

        HttpResponse response = HttpRequest.get(ORDER_PRODUCT_URL)
                .header("Authorization", "Bearer " + token)
                .header("platform", "1")
                .form(params)
                .timeout(10000)
                .execute();

        double totalFee = 0d;
        StringBuilder remark = new StringBuilder();
        if (response.getStatus() == 200) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (jsonObject.getInt("Code") == 0) {
                JSONArray data = jsonObject.getJSONArray("Data");
                if (data != null) {
                    for (int i = 0; i < data.size(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        totalFee += item.getDouble("Fee", 0d);
                        remark.append(item.getStr("ProductName", "")).append("\n");
                    }
                }
            }
        }
        return Pair.of(new BigDecimal(totalFee), remark.toString());
    }

    /**
     * 处理 HTTP 请求 - 根路径
     */
    private void handleRoot(HttpServerRequest req, HttpServerResponse res) {
        String html = "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'><title>凯迪仕抓取服务</title></head>" +
                "<body>" +
                "<h1>凯迪仕工单抓取服务</h1>" +
                "<p>服务状态: <strong>运行中</strong></p>" +
                "<p>HTTP端口: " + SERVER_PORT + "</p>" +
                "<p>上次运行时间: " + lastRunTime + "</p>" +
                "<p>上次运行结果: " + lastRunResult + "</p>" +
                "<hr>" +
                "<h2>API 接口</h2>" +
                "<ul>" +
                "<li><code>GET /health</code> - 健康检查</li>" +
                "<li><code>POST /trigger</code> - 手动触发抓取</li>" +
                "</ul>" +
                "<hr>" +
                "<p>定时任务: 每 2 分钟自动执行一次</p>" +
                "</body></html>";
        res.write(html);
    }

    /**
     * 处理 HTTP 请求 - 健康检查
     */
    private void handleHealth(HttpServerRequest req, HttpServerResponse res) {
        JSONObject health = new JSONObject();
        health.set("status", "UP");
        health.set("port", SERVER_PORT);
        health.set("lastRunTime", lastRunTime);
        health.set("lastRunResult", lastRunResult);
        health.set("isRunning", isRunning);
        res.write(health.toString());
    }

    /**
     * 处理 HTTP 请求 - 手动触发
     */
    private void handleTrigger(HttpServerRequest req, HttpServerResponse res) {
        if (isRunning) {
            JSONObject result = new JSONObject();
            result.set("code", 429);
            result.set("message", "抓取任务正在运行中，请稍后再试");
            res.write(result.toString());
            return;
        }

        // 异步执行抓取任务
        new Thread(() -> {
            start();
        }).start();

        JSONObject result = new JSONObject();
        result.set("code", 200);
        result.set("message", "抓取任务已启动");
        res.write(result.toString());
    }

    /**
     * 主程序启动入口
     */
    public static void main(String[] args) {
        KaadasScraperApp app = new KaadasScraperApp();

        // 启动 HTTP 服务，监听 2333 端口
        HttpUtil.createServer(SERVER_PORT)
                .addAction("/", app::handleRoot)
                .addAction("/health", app::handleHealth)
                .addAction("/trigger", app::handleTrigger)
                .start();
        System.out.println(">>> HTTP 服务已启动，监听端口: " + SERVER_PORT);

        // 1. 程序启动后立即执行一次抓取
        app.start();

        // 2. 设置定时任务：每 2 分钟执行一次 (使用 Hutool Cron)
        CronUtil.schedule("0 0/2 * * * ?", (Task) () -> {
            app.start();
        });

        // 支持秒级定时匹配
        CronUtil.setMatchSecond(true);
        // 启动 Cron 调度器
        CronUtil.start();

        System.out.println(">>> 凯迪仕抓取服务已启动，每 2 分钟执行一次...");
    }
}

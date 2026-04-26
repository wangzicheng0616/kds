package com.kaadas.scraper.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 地址编码工具类
 * 负责从 city.json 中根据省市区名称匹配编码
 */
public class AddressCodeUtil {

    private static JSONArray cityData = null;

    private static synchronized void loadCityData() {
        if (cityData != null) {
            return;
        }
        String path = "json/city.json";
        try {
            try (InputStream is = AddressCodeUtil.class.getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    String content = IOUtils.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
                    cityData = JSONArray.parseArray(content);
                } else {
                    System.err.println("无法在类路径下找到 " + path);
                }
            }
        } catch (Exception e) {
            System.err.println("加载 " + path + " 失败: " + e.getMessage());
        }
    }

    public static String[] getCodes(String province, String city, String area) {
        if (cityData == null) {
            loadCityData();
        }

        String[] results = {"0", "0", "0"};
        if (cityData == null || province == null || province.trim().isEmpty()) {
            return results;
        }

        JSONObject pObj = findNode(cityData, province);
        if (pObj != null) {
            results[0] = pObj.getString("value");
            JSONArray pChildren = pObj.getJSONArray("children");
            
            if (pChildren != null && city != null && !city.trim().isEmpty()) {
                JSONObject cObj = findNode(pChildren, city);
                if (cObj != null) {
                    results[1] = cObj.getString("value");
                    JSONArray cChildren = cObj.getJSONArray("children");
                    
                    if (cChildren != null && area != null && !area.trim().isEmpty()) {
                        JSONObject aObj = findNode(cChildren, area);
                        if (aObj != null) {
                            results[2] = aObj.getString("value");
                        }
                    }
                }
            }
        }
        return results;
    }

    private static JSONObject findNode(JSONArray array, String name) {
        if (array == null || name == null) return null;
        String cleanName = cleanName(name);
        
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String text = obj.getString("text");
            if (text != null && text.equals(name)) return obj;
        }
        
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String text = obj.getString("text");
            if (text != null) {
                String cleanText = cleanName(text);
                if (cleanText.equals(cleanName) || cleanText.contains(cleanName) || cleanName.contains(cleanText)) {
                    return obj;
                }
            }
        }
        return null;
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("(省|市|区|县|特别行政区|壮族自治区|回族自治区|维吾尔自治区|内蒙古自治区|自治区|自治州|盟|旗)$", "");
    }
}

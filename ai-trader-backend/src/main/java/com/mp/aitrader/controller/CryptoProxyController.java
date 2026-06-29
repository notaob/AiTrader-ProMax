package com.mp.aitrader.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Binance 行情数据代理接口
 * 解决中国大陆用户浏览器无法直连 Binance 的问题
 * 所有请求从服务器端发起，支持条件 HTTP 代理
 */
@Slf4j
@RestController
@RequestMapping("/market/btc")
public class CryptoProxyController {

    private static final String BINANCE_API_URL = "https://api.binance.com/api/v3";

    @Value("${proxy.enable:false}")
    private boolean proxyEnable;

    @Value("${proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${proxy.port:7890}")
    private Integer proxyPort;

    /**
     * 获取 BTC 当前价格
     * GET /market/btc/price
     */
    @GetMapping("/price")
    public Map<String, Object> getPrice() {
        try {
            String url = BINANCE_API_URL + "/ticker/price?symbol=BTCUSDT";
            String body = executeBinanceRequest(url);
            JSONObject json = JSONUtil.parseObj(body);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 1);
            result.put("price", json.getStr("price"));
            return result;
        } catch (Exception e) {
            log.error("获取BTC价格失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 0);
            result.put("msg", "获取价格失败");
            return result;
        }
    }

    /**
     * 获取 BTC K线数据
     * GET /market/btc/klines?interval=1h&limit=25
     * 支持 interval: 1h, 4h, 1d 等; limit: 自定义
     */
    @GetMapping("/klines")
    public Object getKlines(
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "25") Integer limit) {
        try {
            String url = BINANCE_API_URL + "/klines?symbol=BTCUSDT&interval=" + interval + "&limit=" + limit;
            String body = executeBinanceRequest(url);
            return JSONUtil.parseArray(body);
        } catch (Exception e) {
            log.error("获取BTC K线数据失败 interval={} limit={}", interval, limit, e);
            return JSONUtil.parseArray("[]");
        }
    }

    /**
     * 执行 Binance HTTP 请求（支持条件代理）
     */
    private String executeBinanceRequest(String url) {
        HttpRequest request = HttpRequest.get(url)
                .timeout(10000);

        if (proxyEnable) {
            log.info("使用代理访问 Binance: {}:{}", proxyHost, proxyPort);
            request.setHttpProxy(proxyHost, proxyPort);
        }

        return request.execute().body();
    }
}

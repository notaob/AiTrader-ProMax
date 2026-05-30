package com.mp.aitrader.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CryptoMarketService {

    // 币安 API
    private static final String BINANCE_API_URL = "https://api.binance.com/api/v3/klines";

    private final StringRedisTemplate redisTemplate;

    @Value("${proxy.enable:false}")
    private boolean proxyEnable;

    @Value("${proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${proxy.port:7890}")
    private Integer proxyPort;

    public CryptoMarketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取 BTC 最近 30 天的详细行情数据
     * 优先从 Redis 读取 Python Agent 推送的实时数据
     */
    public String getBtcMarketData() {
        try {
            // 从 Redis 获取 Python Agent 推送的实时价格
            BigDecimal realtimePrice = null;
            String marketData = redisTemplate.opsForValue().get("market:btcusdt");
            if (marketData != null) {
                try {
                    JSONObject json = JSONUtil.parseObj(marketData);
                    realtimePrice = json.getBigDecimal("currentPrice");
                    log.info("从 Redis 获取到实时价格: {}", realtimePrice);
                } catch (Exception e) {
                    log.warn("解析 Redis 市场数据失败", e);
                }
            }

            // 获取 BTCUSDT 最近 30 天的日线数据
            HttpRequest request = HttpRequest.get(BINANCE_API_URL)
                    .form("symbol", "BTCUSDT")
                    .form("interval", "1d")
                    .form("limit", 30)
                    .timeout(10000);

            // 如果开启了代理，配置代理
            if (proxyEnable) {
                log.info("使用代理访问 Binance API: {}:{}", proxyHost, proxyPort);
                request.setHttpProxy(proxyHost, proxyPort);
            }

            String response = request.execute().body();

            JSONArray klines = JSONUtil.parseArray(response);
            if (klines == null || klines.isEmpty()) {
                return "无法获取市场数据";
            }

            // 提取收盘价列表
            List<BigDecimal> closePrices = new ArrayList<>();
            for (int i = 0; i < klines.size(); i++) {
                closePrices.add(klines.getJSONArray(i).getBigDecimal(4));
            }

            // 如果 Redis 有实时价格，替换最新收盘价
            BigDecimal currentPrice = closePrices.get(closePrices.size() - 1);
            if (realtimePrice != null) {
                currentPrice = realtimePrice;
                closePrices.set(closePrices.size() - 1, realtimePrice);
            }

            // 计算技术指标
            BigDecimal rsi14 = calculateRSI(closePrices, 14);
            BigDecimal ma7 = calculateMA(closePrices, 7);
            BigDecimal ma25 = calculateMA(closePrices, 25);

            // 计算 MACD (12, 26, 9)
            BigDecimal ema12 = calculateEMA(closePrices, 12);
            BigDecimal ema26 = calculateEMA(closePrices, 26);

            // 计算布林带 (20, 2)
            BigDecimal sma20 = calculateMA(closePrices, 20);
            BigDecimal stdDev = calculateStdDev(closePrices, 20, sma20);
            BigDecimal upperBand = sma20.add(stdDev.multiply(new BigDecimal(2)));
            BigDecimal lowerBand = sma20.subtract(stdDev.multiply(new BigDecimal(2)));

            // 统计最近 7 天的数据
            int last7DaysIndex = Math.max(0, klines.size() - 7);
            BigDecimal maxPrice7d = BigDecimal.ZERO;
            BigDecimal minPrice7d = new BigDecimal("999999999");
            BigDecimal totalVolume7d = BigDecimal.ZERO;
            StringBuilder trendBuilder = new StringBuilder();

            for (int i = last7DaysIndex; i < klines.size(); i++) {
                JSONArray kline = klines.getJSONArray(i);
                BigDecimal high = kline.getBigDecimal(2);
                BigDecimal low = kline.getBigDecimal(3);
                BigDecimal close = kline.getBigDecimal(4);
                BigDecimal volume = kline.getBigDecimal(5);

                if (i == klines.size() - 1 && realtimePrice != null) {
                    close = realtimePrice;
                }

                if (high.compareTo(maxPrice7d) > 0) maxPrice7d = high;
                if (low.compareTo(minPrice7d) < 0) minPrice7d = low;
                totalVolume7d = totalVolume7d.add(volume);

                trendBuilder.append(String.format("$%.0f", close));
                if (i < klines.size() - 1) {
                    trendBuilder.append(" -> ");
                }
            }

            // 计算 7 天涨跌幅
            BigDecimal price7DaysAgo = closePrices.get(last7DaysIndex);
            BigDecimal change = currentPrice.subtract(price7DaysAgo);
            BigDecimal changePercent = change.divide(price7DaysAgo, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));

            // 组装详细报告
            StringBuilder marketSummary = new StringBuilder();
            marketSummary.append("BTC 市场深度分析数据 (Source: Binance REST + Python Agent 实时推送)：\n");
            marketSummary.append(String.format("- 当前价格: $%.2f%s\n", currentPrice, realtimePrice != null ? " (实时)" : ""));
            marketSummary.append(String.format("- 7日涨跌幅: %.2f%%\n", changePercent));
            marketSummary.append(String.format("- 7日最高/最低: $%.2f / $%.2f\n", maxPrice7d, minPrice7d));
            marketSummary.append(String.format("- 7日总成交量: %.2f BTC\n", totalVolume7d));
            marketSummary.append("\n【技术指标】\n");
            marketSummary.append(String.format("- RSI (14): %.2f (%s)\n", rsi14, getRsiStatus(rsi14)));
            marketSummary.append(String.format("- MA7 (短期): $%.2f | MA25 (中期): $%.2f\n", ma7, ma25));
            marketSummary.append(String.format("- EMA12: $%.2f | EMA26: $%.2f\n", ema12, ema26));
            marketSummary.append(String.format("- 布林带 (20,2): 上轨 $%.0f | 中轨 $%.0f | 下轨 $%.0f\n", upperBand, sma20, lowerBand));

            // 判断布林带位置
            String bollingerStatus = "中轨附近震荡";
            if (currentPrice.compareTo(upperBand) > 0) bollingerStatus = "突破上轨 (强势/超买)";
            else if (currentPrice.compareTo(lowerBand) < 0) bollingerStatus = "跌破下轨 (弱势/超卖)";
            else if (currentPrice.compareTo(sma20) > 0) bollingerStatus = "中轨上方 (偏强)";
            else bollingerStatus = "中轨下方 (偏弱)";
            marketSummary.append("- 布林带状态: ").append(bollingerStatus).append("\n");

            marketSummary.append("- 均线状态: ").append(currentPrice.compareTo(ma7) > 0 ? "价格在MA7上方" : "价格在MA7下方").append(", ");
            marketSummary.append(ma7.compareTo(ma25) > 0 ? "MA7金叉MA25" : "MA7死叉MA25").append("\n");

            marketSummary.append("\n【价格趋势(近7日)】: ").append(trendBuilder);

            return marketSummary.toString();

        } catch (Exception e) {
            log.error("获取行情失败", e);
            return "获取市场数据失败";
        }
    }

    // 计算 EMA (指数移动平均)
    private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        if (prices.isEmpty()) return BigDecimal.ZERO;
        BigDecimal k = new BigDecimal(2).divide(new BigDecimal(period + 1), 4, RoundingMode.HALF_UP);
        BigDecimal ema = prices.get(0);
        for (int i = 1; i < prices.size(); i++) {
            ema = prices.get(i).multiply(k).add(ema.multiply(BigDecimal.ONE.subtract(k)));
        }
        return ema;
    }

    // 计算标准差 (用于布林带)
    private BigDecimal calculateStdDev(List<BigDecimal> prices, int period, BigDecimal ma) {
        if (prices.size() < period) return BigDecimal.ZERO;
        BigDecimal sumSqDiff = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).subtract(ma);
            sumSqDiff = sumSqDiff.add(diff.pow(2));
        }
        BigDecimal variance = sumSqDiff.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
        return new BigDecimal(Math.sqrt(variance.doubleValue()));
    }

    /**
     * 计算简单移动平均线 (SMA)
     */
    private BigDecimal calculateMA(List<BigDecimal> prices, int period) {
        if (prices.size() < period) return BigDecimal.ZERO;

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i));
        }
        return sum.divide(new BigDecimal(period), 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算相对强弱指标 (RSI)
     */
    private BigDecimal calculateRSI(List<BigDecimal> prices, int period) {
        if (prices.size() <= period) return new BigDecimal(50);

        BigDecimal sumGain = BigDecimal.ZERO;
        BigDecimal sumLoss = BigDecimal.ZERO;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                sumGain = sumGain.add(change);
            } else {
                sumLoss = sumLoss.add(change.abs());
            }
        }

        if (sumLoss.compareTo(BigDecimal.ZERO) == 0) return new BigDecimal(100);

        BigDecimal avgGain = sumGain.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLoss.divide(new BigDecimal(period), 4, RoundingMode.HALF_UP);

        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        BigDecimal rsi = new BigDecimal(100).subtract(
                new BigDecimal(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP)
        );

        return rsi;
    }

    private String getRsiStatus(BigDecimal rsi) {
        if (rsi.compareTo(new BigDecimal(70)) > 0) return "超买";
        if (rsi.compareTo(new BigDecimal(30)) < 0) return "超卖";
        return "中性";
    }
}

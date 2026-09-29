package com.mp.aitrader.task;

import com.mp.aitrader.VO.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对账手动触发入口（T4 验收项：提供一次对账任务的手动触发）。
 *
 * <p>定位：低频运维操作。设计成手动触发而非定时器，是因为：
 * <ul>
 *   <li>对账的价值在于"发现了丢失"，而丢失源（降级路径进程重启、兜底执行失败）本身低频；</li>
 *   <li>主动触发可以在发布后 / 故障后立即执行一次，而不是等下一个周期；</li>
 *   <li>配合指标 {@code ai.vector.reconcile.resubmitted} 持续增长即说明主链路仍有丢失源，需根治。</li>
 * </ul>
 * 需登录（走 JwtInterceptor），生产环境建议加管理员权限控制。
 */
@Slf4j
@RestController
@RequestMapping("/ai/reconcile")
public class AiReconcileController {

    @Autowired
    private AiVectorReconcileService reconcileService;

    /**
     * 手动触发一次向量对账。
     *
     * @param maxResubmit 单次最多补投条数（可选，默认 500，防止 Redis Stack 被清空后全量重推打爆 embedding 配额）
     */
    @PostMapping("/vector")
    public Result<AiVectorReconcileService.ReconcileResult> reconcile(
            @RequestParam(value = "maxResubmit", required = false) Integer maxResubmit) {
        log.info("手动触发向量对账: maxResubmit={}", maxResubmit);
        try {
            return Result.success(reconcileService.reconcile(maxResubmit));
        } catch (IllegalStateException e) {
            log.error("向量对账中止: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}

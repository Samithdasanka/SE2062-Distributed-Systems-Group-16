package com.group16.distributed.payments.time;

import com.group16.distributed.payments.config.NodeProperties;
import org.springframework.stereotype.Component;

@Component
public class TimeService {
    private final NodeProperties nodeProperties;

    public TimeService(NodeProperties nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    public long localNowMs() {
        return System.currentTimeMillis();
    }

    public String nodeId() {
        return nodeProperties.getId();
    }
}
package com.group16.distributed.payments.raft.api;

import com.group16.distributed.payments.time.TimeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/raft")
public class TimeController {
    private final TimeService timeService;

    public TimeController(TimeService timeService) {
        this.timeService = timeService;
    }

    @GetMapping("/time")
    public long time() {
        return timeService.localNowMs();
    }
}
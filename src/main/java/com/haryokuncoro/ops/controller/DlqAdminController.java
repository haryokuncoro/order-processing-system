package com.haryokuncoro.ops.controller;


import com.haryokuncoro.ops.dto.ApiResponse;
import com.haryokuncoro.ops.entity.FailedEvent;
import com.haryokuncoro.ops.service.DlqReplayService;
import com.haryokuncoro.ops.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dlq")
@RequiredArgsConstructor
public class DlqAdminController {

    private final DlqReplayService replayService;

    @PostMapping("/replay/{eventId}")
    public ApiResponse<Void> replay(@PathVariable String eventId) throws Exception {
        replayService.replay(eventId);
        return ResponseUtil.success("Replay triggered");
    }

    @GetMapping
    public ApiResponse<List<FailedEvent>> findAll(@RequestParam(required = false) String topic) {
        List<FailedEvent> events = replayService.findAll(topic);
        return ResponseUtil.success(events);
    }
}
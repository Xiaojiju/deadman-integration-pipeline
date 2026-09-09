package com.mtfm.gateway.catalog.web;

import com.mtfm.gateway.catalog.dto.ChannelProbeRequestBody;
import com.mtfm.gateway.catalog.dto.ChannelProbeView;
import com.mtfm.gateway.catalog.dto.ChannelView;
import com.mtfm.gateway.catalog.dto.ChannelWriteRequest;
import com.mtfm.gateway.catalog.dto.PageResult;
import com.mtfm.gateway.catalog.dto.validation.CreateOp;
import com.mtfm.gateway.catalog.dto.validation.UpdateOp;
import com.mtfm.gateway.catalog.schema.CatalogChannelProbeService;
import com.mtfm.gateway.catalog.schema.CatalogFormService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 共享通道 REST 接口，前缀 {@code /catalog/channels}。
 *
 * <p>示例：{@code GET /catalog/channels?page=1&size=20} 分页列表；
 * {@code POST /catalog/channels} 创建通道。
 */
@Validated
@RestController
@RequestMapping("/catalog/channels")
public class CatalogChannelController {

    private final CatalogFormService forms;
    private final CatalogChannelProbeService probes;

    public CatalogChannelController(CatalogFormService forms, CatalogChannelProbeService probes) {
        this.forms = forms;
        this.probes = probes;
    }

    @GetMapping
    public PageResult<ChannelView> listChannels(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page 从 1 开始") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 至少为 1") @Max(value = 100, message = "size 不能超过 100") int size) {
        return forms.pageChannelViews(page, size);
    }

    @GetMapping("/{channelId}")
    public ChannelView getChannel(@PathVariable String channelId) {
        return forms.requireChannelView(channelId);
    }

    @PostMapping
    public ChannelView createChannel(@Validated(CreateOp.class) @RequestBody ChannelWriteRequest request) {
        return forms.toChannelView(forms.createChannel(request));
    }

    @PutMapping("/{channelId}")
    public ChannelView updateChannel(@PathVariable String channelId,
            @Validated(UpdateOp.class) @RequestBody ChannelWriteRequest request) {
        return forms.toChannelView(forms.updateChannel(channelId, request));
    }

    @DeleteMapping("/{channelId}")
    public Map<String, Object> deleteChannel(@PathVariable String channelId) {
        boolean deleted = forms.deleteChannel(channelId);
        return Map.of("channelId", channelId, "deleted", deleted);
    }

    @PostMapping("/{channelId}/probe")
    public ChannelProbeView probeChannel(
            @PathVariable String channelId,
            @Valid @RequestBody ChannelProbeRequestBody request) {
        return probes.probe(channelId, request);
    }
}

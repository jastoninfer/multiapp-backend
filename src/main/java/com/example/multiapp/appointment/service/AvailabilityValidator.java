package com.example.multiapp.appointment.service;

import com.example.multiapp.common.api.ConflictException;
import com.example.multiapp.common.time.TimeRange;
import com.example.multiapp.resource.entity.ResourceBlock;
import com.example.multiapp.resource.entity.ResourceWorkingHours;
import com.example.multiapp.resource.repo.ResourceBlockRepository;
import com.example.multiapp.resource.repo.ResourceWorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AvailabilityValidator {
    private final ResourceWorkingHoursRepository workingHoursRepo;
    private final ResourceBlockRepository blockRepo;

    /*
    * 检验预约时间窗, 必须:
    * 1) 完全落在"某个" working hours window (按resource timezone展开), 不考虑跨天
    * 2) 不与任何resource_block重叠(半开区间)
    * */
    public void validate(UUID tenantId, UUID resourceUserId,
                         OffsetDateTime startAt, OffsetDateTime endAt) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceUserId);
        Objects.requireNonNull(startAt);
        Objects.requireNonNull(endAt);
        TimeRange appointment = new TimeRange(startAt, endAt);
        // 1) working hours 覆盖
        List<ResourceWorkingHours> rules = workingHoursRepo.findByIdTenantIdAndIdResourceUserId(tenantId,
                resourceUserId);
        if(rules.isEmpty()) throw new ConflictException("Resource has no working hours configured");
        ZoneId zone = validateTimeZone(rules.get(0));
        if(!isCoveredByAnyWorkingWindow(rules, appointment, zone)) {
            throw new ConflictException("appointment is outside resource working hours");
        }
        // 2) blocks 冲突, 只查可能重叠的范围, 提高性能
        // range overlap 条件: block.edn > appt.start AND block.start < appt.end
        List<ResourceBlock> blocks = blockRepo.listInRange(tenantId, resourceUserId, startAt, endAt);
        for (ResourceBlock b : blocks) {
            TimeRange blockRange = new TimeRange(b.getStartAt(), b.getEndAt());
            if (appointment.overlaps(blockRange)) {
                throw new ConflictException("appointment overlaps a resource block: " + b.getReason());
            }
        }
    }

    /*
    * 预约完全被某个单日工作窗口覆盖, 建设预约不跨天(工业级可以支持跨天, v1不需要)
    * */
    private boolean isCoveredByAnyWorkingWindow(List<ResourceWorkingHours> rules, TimeRange t, ZoneId zone) {
        LocalDate startDate = t.start().atZoneSameInstant(zone).toLocalDate();
        LocalDate endDate = t.end().atZoneSameInstant(zone).toLocalDate();
        if(!startDate.equals(endDate)){
            throw new IllegalArgumentException("appointment cannot cross local day boundaries");
        }
        DayOfWeek dow = t.start().atZoneSameInstant(zone).getDayOfWeek();
        int day = dow.getValue(); // 1=Mon, 7=Sun
        for (ResourceWorkingHours h : rules) {
            if(h.getId().getDayOfWeek() != day) continue;
            TimeRange window = normalizeWorkingHoursForDate(h, startDate, zone);
            if(window.covers(t)) return true;
        }
        return false;
    }

    /*
    * 将某天的working hours (LocalTime + timezone) 展开为绝对时间窗 OffsetDateTime
    * DST由 ZonedDateTime自动处理
    * */
    private static TimeRange normalizeWorkingHoursForDate(ResourceWorkingHours h, LocalDate date, ZoneId zone) {
        // ZonedDateTime.of会处理DST, 如果本地时间不存在/重复, 按Java规则解析
        ZonedDateTime zStart = ZonedDateTime.of(date, h.getStartLocal(), zone);
        ZonedDateTime zEnd = ZonedDateTime.of(date, h.getEndLocal(), zone);
        OffsetDateTime start = zStart.toOffsetDateTime();
        OffsetDateTime end = zEnd.toOffsetDateTime();
        return new TimeRange(start, end);
    }

    private ZoneId validateTimeZone(ResourceWorkingHours rule) {
        try {
            return ZoneId.of(rule.getTimezone());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + rule.getTimezone());
        }
    }
}

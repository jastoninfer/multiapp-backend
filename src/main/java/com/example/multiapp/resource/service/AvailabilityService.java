package com.example.multiapp.resource.service;

import com.example.multiapp.appointment.dto.AppointmentSearchQuery;
import com.example.multiapp.appointment.dto.AppointmentSummary;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.resource.auth.ResourceBlockAuthorizer;
import com.example.multiapp.resource.dto.AvailabilityResponse;
import com.example.multiapp.resource.dto.CreateWorkingHoursRequest;
import com.example.multiapp.resource.dto.ResourceBlockResponse;
import com.example.multiapp.resource.dto.WorkingHoursRule;
import com.example.multiapp.resource.entity.ResourceWorkingHours;
import com.example.multiapp.resource.repo.ResourceWorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AvailabilityService {
    private final ResourceWorkingHoursRepository workingHoursRepo;
    private final ResourceBlockService resourceBlockService;
    private final ResourceBlockAuthorizer resourceBlockAuth;
    private final AppointmentRepository appointmentRepo;

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(
            RequestContext ctx, UUID resourceUserId, OffsetDateTime from, OffsetDateTime to) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        resourceBlockAuth.requireList(ctx, resourceUserId); // 复用逻辑
        List<ResourceBlockResponse> blocks = resourceBlockService.list(ctx, resourceUserId, from, to);
        AppointmentSearchQuery query = new AppointmentSearchQuery(resourceUserId,null, null, from, to, null);
        List<AppointmentSummary> appointments = appointmentRepo.search(ctx.tenantId(), query,
                PageRequest.of(0, 20)).getContent();
        // working hours: v1 直接返回规则, 不做计算
        List<WorkingHoursRule> rules =  workingHoursRepo.findByIdTenantIdAndIdResourceUserId(ctx.tenantId(), resourceUserId)
                .stream().map(r -> new WorkingHoursRule(
                        r.getId().getDayOfWeek(),
                        r.getStartLocal().toString(),
                        r.getEndLocal().toString(),
                        r.getTimezone()
                )).toList();
        return new AvailabilityResponse(resourceUserId, blocks, appointments, rules);
    }

    @Transactional
    /*
    * 修改working hours, 不需要ifMatch
    * */
    public void putWorkingHours(
            RequestContext ctx, UUID resourceUserId, CreateWorkingHoursRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(req, "CreateWorkingHoursRequest");
        resourceBlockAuth.requireList(ctx, resourceUserId); // 复用逻辑
        // 校验timezone (IANA)
        validateTimeZone(req.timezone());
//        List<ResourceWorkingHours> old = workingHoursRepo.findByIdTenantIdAndIdResourceUserId(
//                ctx.tenantId(), resourceUserId);
//        if(!old.isEmpty()) workingHoursRepo.deleteAllInBatch(old);

        // 解析+校验规则: day 1...7不重复, end > start
        BitSet bs = new BitSet(8);
        List<ResourceWorkingHours> toSave = new ArrayList<>();
        for (WorkingHoursRule rule : req.rules()) {
            int day = rule.dayOfWeek();
//            if (day < 1 || day > 7)
//                throw new IllegalArgumentException("day of week must be between 1 and 7");
            if(bs.get(day)) {
                throw new IllegalArgumentException("duplicate dayOfWeek: " + day);
            }
            LocalTime start = parseLocalTime(rule.startLocal(), "startLocal");
            LocalTime end = parseLocalTime(rule.endLocal(), "endLocal");
            validateDuration(start, end);
            bs.set(day);
            toSave.add(ResourceWorkingHours
                    .create(ctx.tenantId(), resourceUserId, rule, start, end));
        }
        workingHoursRepo.deleteByTenantIdAndIdResourceUserId(ctx.tenantId(), resourceUserId);
        workingHoursRepo.flush();
        workingHoursRepo.saveAll(toSave);
    }

    private static void validateTimeZone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    private static LocalTime parseLocalTime(String time, String name) {
        try {
            return LocalTime.parse(time);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid %s : %s, expected HH:mm or HH:mm:ss"
                    .formatted(name, time));
        }
    }

    private static void validateDuration(LocalTime start, LocalTime end) {
        if(!end.isAfter(start)) {
            throw new IllegalArgumentException("start should be before end");
        }
    }
}

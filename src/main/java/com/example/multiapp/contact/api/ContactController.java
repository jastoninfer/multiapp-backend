package com.example.multiapp.contact.api;

import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.contact.dto.ContactQuery;
import com.example.multiapp.contact.dto.ContactResponse;
import com.example.multiapp.contact.dto.CreateContactRequest;
import com.example.multiapp.contact.dto.UpdateContactRequest;
import com.example.multiapp.contact.service.ContactService;
import com.example.multiapp.contactclaim.dto.ClaimCodeRequest;
import com.example.multiapp.contactclaim.dto.ClaimCodeResponse;
import com.example.multiapp.contactclaim.dto.ClaimRequest;
import com.example.multiapp.contactclaim.dto.ClaimResult;
import com.example.multiapp.contactclaim.service.ContactClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Request;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/contacts")
public class ContactController {

    private final ContactClaimService contactClaimService;
    private final ContactService contactService;

    /*
    * 生成code
    * */
    @PostMapping("/{contactId}/claim-codes")
    public ClaimCodeResponse issueCode(
            @PathVariable UUID contactId,
            @Valid @RequestBody ClaimCodeRequest body,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        return contactClaimService.issueClaimCode(ctx, contactId, body);
    }

    /*
    * 消费code
    * */
    @PostMapping("/claim")
    public ClaimResult consumeCode(
            @Valid @RequestBody ClaimRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return contactClaimService.claim(ctx, body);
    }

    /*
    * create Contact
    * */
    @PostMapping
    public ResponseEntity<ContactResponse> create(
            @Valid @RequestBody CreateContactRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        ContactResponse resp = contactService.addContact(ctx, body);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(resp);
    }

    @GetMapping("/{contactId}")
    public ResponseEntity<ContactResponse> get(
            @PathVariable UUID contactId,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        ContactResponse resp = contactService.get(ctx, contactId);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.ok().eTag(etag).body(resp);
    }

    @GetMapping
    public PageResponse<ContactResponse> list(
            @Valid @NotNull ContactQuery query,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(contactService.list(ctx, query, pageable));
    }

    @PatchMapping("/{contactId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID contactId,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody UpdateContactRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        contactService.update(ctx, contactId, ifMatch, body);
        return ResponseEntity.noContent().build();
    }
}

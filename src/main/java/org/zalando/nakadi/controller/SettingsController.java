package org.zalando.nakadi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.nakadi.config.SecuritySettings;
import org.zalando.nakadi.domain.ItemsWrapper;
import org.zalando.nakadi.domain.ResourceAuthorization;
import org.zalando.nakadi.exceptions.runtime.UnknownOperationException;
import org.zalando.nakadi.security.Client;
import org.zalando.nakadi.service.AdminService;
import org.zalando.nakadi.service.BlacklistService;
import org.zalando.nakadi.util.FeatureToggleService;
import org.zalando.problem.Problem;
import org.zalando.problem.spring.web.advice.Responses;

import javax.validation.Valid;
import javax.ws.rs.core.Response;

@RestController
@RequestMapping(value = "/settings")
public class SettingsController {

    private final BlacklistService blacklistService;
    private final FeatureToggleService featureToggleService;
    private final SecuritySettings securitySettings;
    private final AdminService adminService;

    private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);


    @Autowired
    public SettingsController(final BlacklistService blacklistService,
                              final FeatureToggleService featureToggleService,
                              final SecuritySettings securitySettings,
                              final AdminService adminService) {
        this.blacklistService = blacklistService;
        this.featureToggleService = featureToggleService;
        this.securitySettings = securitySettings;
        this.adminService = adminService;
    }

    @RequestMapping(path = "/blacklist", method = RequestMethod.GET)
    public ResponseEntity<?> getBlacklist(final Client client) {
        if (isNotAdmin(client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(blacklistService.getBlacklist());
    }

    @RequestMapping(value = "/blacklist/{blacklist_type}/{name}", method = RequestMethod.PUT)
    public ResponseEntity blacklist(@PathVariable("blacklist_type") final BlacklistService.Type blacklistType,
                                        @PathVariable("name") final String name,
                                        final Client client) {
        if (isNotAdmin(client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        blacklistService.blacklist(name, blacklistType);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/blacklist/{blacklist_type}/{name}", method = RequestMethod.DELETE)
    public ResponseEntity whitelist(@PathVariable("blacklist_type") final BlacklistService.Type blacklistType,
                                         @PathVariable("name") final String name,
                                         final Client client) {
        if (isNotAdmin(client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        blacklistService.whitelist(name, blacklistType);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(path = "/features", method = RequestMethod.GET)
    public ResponseEntity<?> getFeatures(final Client client) {
        if (isNotAdmin(client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(new ItemsWrapper<>(featureToggleService.getFeatures()));
    }

    @RequestMapping(path = "/features", method = RequestMethod.POST)
    public ResponseEntity<?> setFeature(@RequestBody final FeatureToggleService.FeatureWrapper featureWrapper,
                                        final Client client) {
        if (isNotAdmin(client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        featureToggleService.setFeature(featureWrapper);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(path = "/admins", method = RequestMethod.GET)
    public ResponseEntity<?> getAdmins() {
        return ResponseEntity.ok(ResourceAuthorization.fromPermissionsList(adminService.getAdmins()));
    }

    @RequestMapping(path = "/admins", method = RequestMethod.POST)
    public ResponseEntity<?> updateAdmins(@Valid @RequestBody final ResourceAuthorization authz) {
        adminService.updateAdmins(authz.toPermissionsList("nakadi"));
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(UnknownOperationException.class)
    public ResponseEntity<Problem> handleUnknownOperationException(final RuntimeException ex,
                                                                   final NativeWebRequest request) {
        LOG.error(ex.getMessage(), ex);
        return Responses.create(Response.Status.SERVICE_UNAVAILABLE,
                "There was a problem processing your request.", request);
    }

    private boolean isNotAdmin(final Client client) {
        return !client.getClientId().equals(securitySettings.getAdminClientId());
    }
}

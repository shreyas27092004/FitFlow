package com.fitflow.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-admin")
public class TestAdminProtectedController {

    @GetMapping("/class-edit")
    @PreAuthorize("@perm.has('CLASS_EDIT')")
    public String classEditAction() {
        return "class-edit-ok";
    }

    @PostMapping("/refund-approve")
    @PreAuthorize("@perm.has('REFUND_APPROVE')")
    public String refundApproveAction() {
        return "refund-approve-ok";
    }
}
